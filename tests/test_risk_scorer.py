import pytest
from engine.risk_scorer import Action, RiskDecision, RiskScorer

@pytest.fixture
def scorer():
    return RiskScorer(
        ocsvm_weight=0.6,
        mahal_weight=0.4,
        base_warn_thresh=0.35,
        step_up_thresh=0.60,
        block_thresh=0.80,
        adaptive_window=20,
        adaptive_k=1.5,
    )

# ---------------------------------------------------------------------------
# Score fusion
# ---------------------------------------------------------------------------
class TestScoreFusion:
    def test_zero_scores_produce_zero_composite(self, scorer):
        d = scorer.score(0.0, 0.0)
        assert d.composite_score == 0.0

    def test_one_scores_produce_one_composite(self, scorer):
        d = scorer.score(1.0, 1.0)
        assert d.composite_score == 1.0

    def test_weighted_fusion_math(self, scorer):
        # OCSVM weight=0.6, Mahal weight=0.4
        d = scorer.score(0.5, 0.0)
        expected = 0.6 * 0.5 + 0.4 * 0.0
        assert abs(d.composite_score - expected) < 1e-9

    def test_ocsvm_and_mahal_stored_in_decision(self, scorer):
        d = scorer.score(0.3, 0.7, keystroke_count=25)
        assert abs(d.ocsvm_score  - 0.3) < 1e-9
        assert abs(d.mahal_score  - 0.7) < 1e-9
        assert d.keystroke_count == 25

    def test_clamping_above_one(self, scorer):
        """Scores > 1 should be clamped to 1."""
        d = scorer.score(1.5, 1.5)
        assert d.composite_score == 1.0

    def test_clamping_below_zero(self, scorer):
        d = scorer.score(-0.5, -0.5)
        assert d.composite_score == 0.0

# ---------------------------------------------------------------------------
# Action mapping
# ---------------------------------------------------------------------------
class TestActionMapping:
    def test_allow_below_warn(self, scorer):
        # Composite = 0.6*0.1 + 0.4*0.1 = 0.1 → ALLOW
        d = scorer.score(0.1, 0.1)
        assert d.action == Action.ALLOW

    def test_warn_between_thresholds(self, scorer):
        # Composite = 0.6*0.5 + 0.4*0.5 = 0.5 → WARN (between 0.35 and 0.60)
        d = scorer.score(0.5, 0.5)
        assert d.action == Action.WARN

    def test_step_up_threshold(self, scorer):
        # Composite = 0.6*0.7 + 0.4*0.7 = 0.70 → STEP_UP_AUTH
        d = scorer.score(0.7, 0.7)
        assert d.action == Action.STEP_UP_AUTH

    def test_block_at_high_score(self, scorer):
        # Composite = 0.6*0.9 + 0.4*0.9 = 0.90 → BLOCK_SESSION
        d = scorer.score(0.9, 0.9)
        assert d.action == Action.BLOCK_SESSION

    def test_boundary_at_step_up_threshold(self, scorer):
        # Composite exactly at 0.60 → STEP_UP_AUTH
        d = scorer.score(0.60, 0.60)
        assert d.action == Action.STEP_UP_AUTH

    def test_boundary_at_block_threshold(self, scorer):
        # Composite exactly at 0.80 → BLOCK_SESSION
        d = scorer.score(0.80, 0.80)
        assert d.action == Action.BLOCK_SESSION

# ---------------------------------------------------------------------------
# is_anomaly flag
# ---------------------------------------------------------------------------
class TestIsAnomaly:
    def test_allow_not_anomaly(self, scorer):
        d = scorer.score(0.1, 0.1)
        assert d.is_anomaly is False

    def test_warn_not_anomaly(self, scorer):
        d = scorer.score(0.5, 0.5)
        assert d.is_anomaly is False

    def test_step_up_is_anomaly(self, scorer):
        d = scorer.score(0.7, 0.7)
        assert d.is_anomaly is True

    def test_block_is_anomaly(self, scorer):
        d = scorer.score(0.9, 0.9)
        assert d.is_anomaly is True

# ---------------------------------------------------------------------------
# Adaptive threshold
# ---------------------------------------------------------------------------
class TestAdaptiveThreshold:
    def test_initial_threshold_is_base(self, scorer):
        # With no history, adaptive threshold equals base_warn_thresh.
        d = scorer.score(0.1, 0.1)
        assert d.threshold_used == scorer.base_warn_thresh

    def test_threshold_changes_after_history_fills(self):
        # After many low-score windows, adaptive threshold should shift up.
        scorer_a = RiskScorer(
            base_warn_thresh=0.35,
            step_up_thresh=0.60,
            adaptive_window=10,
            adaptive_k=1.5,
        )
        # Feed many very low scores to fill history
        for _ in range(15):
            scorer_a.score(0.02, 0.02)

        d = scorer_a.score(0.02, 0.02)
        # Threshold should now be above the base
        assert d.threshold_used >= scorer_a.base_warn_thresh

    def test_reset_clears_history(self, scorer):
        for _ in range(20):
            scorer.score(0.01, 0.01)
        scorer.reset_history()
        d = scorer.score(0.1, 0.1)
        # After reset, threshold should be back at base
        assert d.threshold_used == scorer.base_warn_thresh
