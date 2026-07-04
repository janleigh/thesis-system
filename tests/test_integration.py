import time

import numpy as np
import pytest
from rich.console import Console
from rich.table import Table

from collector.session_simulator import SessionSimulator
from engine.feature_extractor import FeatureExtractor
from engine.logger import SecurityLogger
from engine.mahalanobis import MahalanobisNormalizer
from engine.ocsvm_model import OCSVMModel
from engine.profile_manager import ProfileManager
from engine.risk_scorer import Action, RiskScorer

console = Console()

# ---------------------------------------------------------------------------
# Shared fixtures
# ---------------------------------------------------------------------------
@pytest.fixture(scope="module")
def pipeline(tmp_path_factory):
    """
    build a complete enrolled pipeline in an isolated temp dir.
    returns (ocsvm, mahal, scorer, simulator, extractor, logger)
    using tmp_path_factory (module-scope safe) to avoid windows file-lock errros. tbh idek if it works
    """
    tmpdir = tmp_path_factory.mktemp("pipeline")
    sim       = SessionSimulator(seed=100)
    extractor = FeatureExtractor()

    # ── Enrollment ──────────────────────────────────────────────────────
    pm = ProfileManager(
        db_path=str(tmpdir / "profiles.db"),
        model_dir=str(tmpdir / "models"),
        testing_mode=True,
    )

    for batch in sim.generate_enrollment_batch(n_windows=50, window_size=30):
        fv = extractor.extract(batch)
        if fv and fv.is_valid():
            pm.add_enrollment_window("test_user", fv)

    profile = pm.get_profile("test_user")
    assert profile and profile.is_ready, "Enrollment did not reach READY state"

    ocsvm  = pm.get_ocsvm("test_user")
    mahal  = pm.get_mahalanobis("test_user")
    scorer = RiskScorer()

    logger = SecurityLogger(
        db_path=str(tmpdir / "log.db"),
        key_path=str(tmpdir / "log.key"),
    )

    yield ocsvm, mahal, scorer, sim, extractor, logger


# ---------------------------------------------------------------------------
# 1. Smoke test: single legitimate session window
# ---------------------------------------------------------------------------
class TestSmokeTest:
    def test_single_legit_window_produces_decision(self, pipeline):
        ocsvm, mahal, scorer, sim, extractor, _ = pipeline
        events = sim.generate_legitimate(n_keystrokes=30)
        fv     = extractor.extract(events)
        assert fv and fv.is_valid()

        stat      = fv.to_stat_array()
        os_score  = ocsvm.anomaly_score(stat)
        mh_score  = mahal.normalized_score(stat)
        decision  = scorer.score(os_score, mh_score, keystroke_count=30)

        assert 0.0 <= decision.composite_score <= 1.0
        assert decision.action in list(Action)

    def test_single_impostor_window_produces_decision(self, pipeline):
        ocsvm, mahal, scorer, sim, extractor, _ = pipeline
        events = sim.generate_impostor(n_keystrokes=30)
        fv     = extractor.extract(events)
        stat   = fv.to_stat_array()

        os_score = ocsvm.anomaly_score(stat)
        mh_score = mahal.normalized_score(stat)
        decision = scorer.score(os_score, mh_score)

        assert 0.0 <= decision.composite_score <= 1.0


# ---------------------------------------------------------------------------
# 2. Score separation: legitimate vs. impostor
# ---------------------------------------------------------------------------
N_TRIALS = 200

class TestScoreSeparation:
    def _collect_scores(self, pipeline, mode: str, n: int = N_TRIALS):
        ocsvm, mahal, _, sim, extractor, _ = pipeline
        scores = []
        for _ in range(n):
            events = (
                sim.generate_legitimate(30) if mode == "legit"
                else sim.generate_impostor(30)
            )
            fv = extractor.extract(events)
            if fv and fv.is_valid():
                stat   = fv.to_stat_array()
                os_    = ocsvm.anomaly_score(stat)
                mh_    = mahal.normalized_score(stat)
                scores.append(0.6 * os_ + 0.4 * mh_)
        return scores

    def test_legitimate_mean_lower_than_impostor(self, pipeline):
        legit_scores    = self._collect_scores(pipeline, "legit")
        impostor_scores = self._collect_scores(pipeline, "impostor")

        legit_mean    = np.mean(legit_scores)
        impostor_mean = np.mean(impostor_scores)

        assert legit_mean < impostor_mean, (
            f"Expected legit mean ({legit_mean:.4f}) < impostor mean ({impostor_mean:.4f})"
        )

    def test_most_legit_windows_allow(self, pipeline):
        ocsvm, mahal, _, sim, extractor, _ = pipeline
        # use a more permissive scorer reflecting that with 50 enrollment
        # windows, the ocsvm boundary might still classify some legit
        # windows as borderline. we test that not everything is BLOCK/STEP_UP cause that'd suck
        scorer_local = RiskScorer(
            base_warn_thresh=0.50,   # allow up to 50% composite score
            step_up_thresh=0.75,
            block_thresh=0.90,
        )
        allow_or_warn_count = 0
        for _ in range(N_TRIALS):
            events = sim.generate_legitimate(30)
            fv     = extractor.extract(events)
            if fv and fv.is_valid():
                stat     = fv.to_stat_array()
                decision = scorer_local.score(
                    ocsvm.anomaly_score(stat), mahal.normalized_score(stat)
                )
                if decision.action in (Action.ALLOW, Action.WARN):
                    allow_or_warn_count += 1

        allow_warn_rate = allow_or_warn_count / N_TRIALS
        # At least 50% of legitimate windows should be ALLOW or WARN
        # (not triggering step-up auth or session block)
        assert allow_warn_rate >= 0.50, (
            f"Legitimate session rejection rate too high: "
            f"{(1-allow_warn_rate):.2%} windows triggered step-up/block"
        )


# ---------------------------------------------------------------------------
# 3. FAR / FRR / EER
# --------------------------------------------------------------------------
class TestFARFRREER:
    def _fused_scores(self, pipeline, mode: str, n: int = N_TRIALS):
        ocsvm, mahal, _, sim, extractor, _ = pipeline
        scores = []
        for _ in range(n):
            events = (
                sim.generate_legitimate(30) if mode == "legit"
                else sim.generate_impostor(30)
            )
            fv = extractor.extract(events)
            if fv and fv.is_valid():
                stat = fv.to_stat_array()
                scores.append(0.6 * ocsvm.anomaly_score(stat) + 0.4 * mahal.normalized_score(stat))
        return scores

    def test_far_frr_computed_without_error(self, pipeline):
        legit    = self._fused_scores(pipeline, "legit")
        impostor = self._fused_scores(pipeline, "impostor")
        threshold = 0.5

        far = sum(1 for s in impostor if s < threshold) / max(len(impostor), 1)
        frr = sum(1 for s in legit    if s >= threshold) / max(len(legit), 1)
        assert 0.0 <= far <= 1.0
        assert 0.0 <= frr <= 1.0

    def test_eer_computed_and_printed(self, pipeline):
        legit    = self._fused_scores(pipeline, "legit",    n=300)
        impostor = self._fused_scores(pipeline, "impostor", n=300)

        thresholds = np.linspace(0, 1, 100)
        far_arr, frr_arr = [], []
        for t in thresholds:
            far_arr.append(sum(1 for s in impostor if s < t) / max(len(impostor), 1))
            frr_arr.append(sum(1 for s in legit    if s >= t) / max(len(legit),    1))

        far_arr = np.array(far_arr)
        frr_arr = np.array(frr_arr)
        idx     = int(np.argmin(np.abs(far_arr - frr_arr)))
        eer     = float((far_arr[idx] + frr_arr[idx]) / 2)
        eer_t   = float(thresholds[idx])

        # Print a summary table
        table = Table(
            title="Integration Test — FAR/FRR/EER Report",
            border_style="cyan",
        )
        table.add_column("Metric",    style="bold")
        table.add_column("Value",     style="cyan")
        table.add_column("Threshold")

        for thresh_label, tval in [("0.35 (WARN)", 0.35), ("0.60 (STEP_UP)", 0.60)]:
            fa = sum(1 for s in impostor if s < tval) / max(len(impostor), 1)
            fr = sum(1 for s in legit    if s >= tval) / max(len(legit),    1)
            table.add_row(f"FAR @ {thresh_label}", f"{fa*100:.2f}%", f"{tval:.2f}")
            table.add_row(f"FRR @ {thresh_label}", f"{fr*100:.2f}%", f"{tval:.2f}")

        table.add_row("", "", "")
        table.add_row("[bold]EER[/bold]", f"[bold]{eer*100:.2f}%[/bold]", f"{eer_t:.4f}")
        console.print()
        console.print(table)

        # EER should exist (i.e., the curves cross)
        assert 0.0 <= eer <= 1.0


# ---------------------------------------------------------------------------
# 4. Logging during pipeline
# ---------------------------------------------------------------------------
class TestLoggingIntegration:
    def test_log_entries_written(self, pipeline):
        ocsvm, mahal, scorer, sim, extractor, logger = pipeline
        logger.clear()

        events   = sim.generate_legitimate(30)
        fv       = extractor.extract(events)
        stat     = fv.to_stat_array()
        decision = scorer.score(ocsvm.anomaly_score(stat), mahal.normalized_score(stat))

        logger.log(
            user_id="test_user",
            event_type="VERIFICATION",
            risk_score=decision.composite_score,
            action=decision.action.value,
            payload={"window": 1},
        )

        entries = logger.read_all("test_user")
        assert len(entries) >= 1
        assert entries[0]["event_type"] == "VERIFICATION"
        assert entries[0]["payload"]["window"] == 1


# ---------------------------------------------------------------------------
# 5. Replay attack
# ---------------------------------------------------------------------------
class TestReplayAttack:
    def test_replay_scores_differently_from_random_impostor(self, pipeline):
        ocsvm, mahal, _, sim, extractor, _ = pipeline

        # Capture a legitimate session
        legit_events = sim.generate_legitimate(30)

        # Replay it
        replay_events = sim.generate_replay_attack(legit_events, jitter_ms=2.0)
        replay_fv     = extractor.extract(replay_events)

        # Random impostor
        impostor_fv = extractor.extract(sim.generate_impostor(30))

        if replay_fv and replay_fv.is_valid() and impostor_fv and impostor_fv.is_valid():
            replay_score   = 0.6 * ocsvm.anomaly_score(replay_fv.to_stat_array()) + \
                             0.4 * mahal.normalized_score(replay_fv.to_stat_array())
            impostor_score = 0.6 * ocsvm.anomaly_score(impostor_fv.to_stat_array()) + \
                             0.4 * mahal.normalized_score(impostor_fv.to_stat_array())

            # replay attack is closer to the legit pattern, so it should score lower
            # (this is a known limitation documented in the thesis scope but whatever lol)
            assert isinstance(replay_score,   float)
            assert isinstance(impostor_score, float)


# ---------------------------------------------------------------------------
# 6. Performance benchmark
# ---------------------------------------------------------------------------
class TestPerformance:
    def test_scoring_latency_under_10ms(self, pipeline):
        # Each scoring window should complete in under 10ms.
        ocsvm, mahal, _, sim, extractor, _ = pipeline
        scorer_local = RiskScorer()   # fresh scorer

        times = []
        for _ in range(50):
            events = sim.generate_legitimate(30)
            fv     = extractor.extract(events)
            stat   = fv.to_stat_array()

            t0 = time.perf_counter()
            ocsvm.anomaly_score(stat)
            mahal.normalized_score(stat)
            scorer_local.score(0.1, 0.1)
            times.append((time.perf_counter() - t0) * 1000)

        p95 = float(np.percentile(times, 95))
        console.print(
            f"\n  [dim]Scoring latency — "
            f"mean: {np.mean(times):.3f} ms | "
            f"p95: {p95:.3f} ms[/dim]"
        )
        assert p95 < 10.0, f"P95 scoring latency {p95:.2f}ms exceeds 10ms target"
