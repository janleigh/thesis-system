"""
i just followed ur proposal -nes

Usage
    python -m cli.evaluate
    python -m cli.evaluate --windows 200    # run more trials for better statistics
    python -m cli.evaluate --thresholds 50  # sweep this many threshold points for EER
"""

import argparse
import time

import numpy as np
from rich.console import Console
from rich.panel import Panel
from rich.table import Table

from collector.session_simulator import SessionSimulator
from engine.feature_extractor import FeatureExtractor
from engine.mahalanobis import MahalanobisNormalizer
from engine.ocsvm_model import OCSVMModel
from engine.risk_scorer import RiskScorer

console = Console()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _simulate_enrollment(simulator: SessionSimulator, extractor: FeatureExtractor,
                        n_windows: int) -> list:
    """Return a list of 6-d stat arrays for OCSVM/Mahal fitting."""
    vectors = []
    for events in simulator.generate_enrollment_batch(n_windows=n_windows, window_size=30):
        fv = extractor.extract(events)
        if fv and fv.is_valid():
            vectors.append(fv.to_stat_array())
    return vectors

def _compute_scores(ocsvm: OCSVMModel, mahal: MahalanobisNormalizer,
                    vectors: list) -> list:
    """Return a list of (ocsvm_raw, mahal_raw) score pairs."""
    scores = []
    for v in vectors:
        os_ = ocsvm.anomaly_score(v)
        ms_ = mahal.normalized_score(v)
        scores.append((os_, ms_))
    return scores

def _fused(os_, ms_, ow=0.6, mw=0.4) -> float:
    return float(np.clip(ow * os_ + mw * ms_, 0.0, 1.0))

def _compute_far_frr(legit_scores: list, impostor_scores: list,
                    n_thresholds: int = 50):
    """
    Sweep thresholds ∈ [0, 1] and return arrays of (threshold, FAR, FRR).
    A sample is rejected (classified as anomaly) if score >= threshold.
    """
    thresholds = np.linspace(0.0, 1.0, n_thresholds)
    far_arr    = []
    frr_arr    = []

    for thresh in thresholds:
        # FAR: proportion of impostor sessions NOT rejected (falsely accepted)
        fa = sum(1 for s in impostor_scores if s < thresh)
        far_arr.append(fa / max(len(impostor_scores), 1))

        # FRR: proportion of legitimate sessions rejected (falsely rejected)
        fr = sum(1 for s in legit_scores if s >= thresh)
        frr_arr.append(fr / max(len(legit_scores), 1))

    return thresholds, np.array(far_arr), np.array(frr_arr)

def _find_eer(thresholds, far_arr, frr_arr) -> tuple:
    """Find the Equal Error Rate — threshold where FAR ≈ FRR."""
    diffs = np.abs(far_arr - frr_arr)
    idx   = int(np.argmin(diffs))
    eer   = float((far_arr[idx] + frr_arr[idx]) / 2)
    return float(thresholds[idx]), eer


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(
        description="Run the full FAR/FRR/EER evaluation suite."
    )
    parser.add_argument("--windows",    type=int, default=200,
                        help="Number of scoring windows per scenario (default 200)")
    parser.add_argument("--enroll",     type=int, default=30,
                        help="Number of enrollment windows (default 30)")
    parser.add_argument("--thresholds", type=int, default=100,
                        help="Threshold sweep points for EER curve (default 100)")
    args = parser.parse_args()

    console.rule("[bold cyan]Biometric Authentication — Evaluation Suite[/bold cyan]")
    console.print(
        f"\n  Enrollment windows : [cyan]{args.enroll}[/cyan]\n"
        f"  Scoring windows    : [cyan]{args.windows}[/cyan] per scenario\n"
        f"  Threshold points   : [cyan]{args.thresholds}[/cyan]\n"
    )

    simulator = SessionSimulator(seed=42)
    extractor = FeatureExtractor()

    # ── Step 1: Enroll ───────────────────────────────────────────────────────
    console.print("[bold]Step 1:[/bold] Generating synthetic enrollment data...", end=" ")
    t0             = time.perf_counter()
    train_vectors  = _simulate_enrollment(simulator, extractor, n_windows=args.enroll)
    enroll_time_ms = (time.perf_counter() - t0) * 1000
    console.print(
        f"[green]done[/green] "
        f"({len(train_vectors)} vectors, {enroll_time_ms:.1f} ms)"
    )

    if len(train_vectors) < 5:
        console.print(
            "[red]✘ Not enough valid enrollment windows. "
            "Increase --enroll and try again.[/red]"
        )
        return

    # ── Step 2: Train models ─────────────────────────────────────────────────
    console.print("[bold]Step 2:[/bold] Training OCSVM + Mahalanobis...", end=" ")
    t0 = time.perf_counter()
    ocsvm = OCSVMModel()
    ocsvm.train(train_vectors)

    mahal = MahalanobisNormalizer()
    mahal.fit(train_vectors)
    train_time_ms = (time.perf_counter() - t0) * 1000
    console.print(
        f"[green]done[/green] "
        f"({train_time_ms:.1f} ms  |  "
        f"support vectors: {ocsvm.summary()['n_support_vectors']})"
    )

    # ── Step 3: Generate test windows ────────────────────────────────────────
    console.print("[bold]Step 3:[/bold] Generating test sessions...", end=" ")

    legit_vectors    = []
    impostor_vectors = []

    t0 = time.perf_counter()
    for _ in range(args.windows):
        ev = simulator.generate_legitimate(n_keystrokes=30)
        fv = extractor.extract(ev)
        if fv and fv.is_valid():
            legit_vectors.append(fv.to_stat_array())

    for _ in range(args.windows):
        ev = simulator.generate_impostor(n_keystrokes=30)
        fv = extractor.extract(ev)
        if fv and fv.is_valid():
            impostor_vectors.append(fv.to_stat_array())

    gen_time_ms = (time.perf_counter() - t0) * 1000
    console.print(
        f"[green]done[/green] "
        f"(legit: {len(legit_vectors)}, impostor: {len(impostor_vectors)}, "
        f"{gen_time_ms:.1f} ms)"
    )

    # ── Step 4: Score ────────────────────────────────────────────────────────
    console.print("[bold]Step 4:[/bold] Scoring all windows...", end=" ")
    t0 = time.perf_counter()

    legit_raw    = _compute_scores(ocsvm, mahal, legit_vectors)
    impostor_raw = _compute_scores(ocsvm, mahal, impostor_vectors)

    legit_fused    = [_fused(o, m) for o, m in legit_raw]
    impostor_fused = [_fused(o, m) for o, m in impostor_raw]

    score_time_ms = (time.perf_counter() - t0) * 1000
    score_per_window_us = (score_time_ms / max(len(legit_fused) + len(impostor_fused), 1)) * 1000
    console.print(
        f"[green]done[/green] "
        f"({score_time_ms:.1f} ms total, "
        f"[cyan]{score_per_window_us:.1f} µs[/cyan] per window)"
    )

    # ── Step 5: FAR / FRR / EER ─────────────────────────────────────────────
    console.print("[bold]Step 5:[/bold] Computing FAR / FRR / EER...", end=" ")
    thresholds, far_arr, frr_arr = _compute_far_frr(
        legit_fused, impostor_fused, n_thresholds=args.thresholds
    )
    eer_thresh, eer = _find_eer(thresholds, far_arr, frr_arr)
    console.print("[green]done[/green]\n")

    # ── Results tables ───────────────────────────────────────────────────────
    # Score distribution table
    sdist = Table(title="Score Distribution", border_style="cyan")
    sdist.add_column("Metric",       style="bold")
    sdist.add_column("Legitimate",   style="green")
    sdist.add_column("Impostor",     style="red")

    sdist.add_row("Mean risk score",
                f"{np.mean(legit_fused):.4f}",
                f"{np.mean(impostor_fused):.4f}")
    sdist.add_row("Std risk score",
                f"{np.std(legit_fused):.4f}",
                f"{np.std(impostor_fused):.4f}")
    sdist.add_row("Min risk score",
                f"{np.min(legit_fused):.4f}",
                f"{np.min(impostor_fused):.4f}")
    sdist.add_row("Max risk score",
                f"{np.max(legit_fused):.4f}",
                f"{np.max(impostor_fused):.4f}")
    console.print(sdist)
    console.print()

    # EER table
    eer_table = Table(title="Authentication Performance (ISO/IEC 25010 — Security)", border_style="cyan")
    eer_table.add_column("Metric",   style="bold")
    eer_table.add_column("Value",    style="cyan")
    eer_table.add_column("At Threshold")

    # Compute FAR/FRR at a few meaningful thresholds
    for name, tval in [
        ("Default WARN (0.35)",  0.35),
        ("Step-up (0.60)",       0.60),
        ("Block (0.80)",         0.80),
        ("EER Point",            eer_thresh),
    ]:
        fa = sum(1 for s in impostor_fused if s < tval) / max(len(impostor_fused), 1)
        fr = sum(1 for s in legit_fused    if s >= tval) / max(len(legit_fused), 1)
        eer_table.add_row(
            f"FAR @ {name}",
            f"{fa * 100:.2f}%",
            f"thresh={tval:.2f}",
        )
        eer_table.add_row(
            f"FRR @ {name}",
            f"{fr * 100:.2f}%",
            f"thresh={tval:.2f}",
        )

    eer_table.add_row("", "", "")
    eer_table.add_row(
        "[bold]EER[/bold]",
        f"[bold cyan]{eer * 100:.2f}%[/bold cyan]",
        f"thresh={eer_thresh:.4f}",
    )
    console.print(eer_table)
    console.print()

    # Performance table
    perf_table = Table(title="Computational Performance (ISO/IEC 25010 — Efficiency)", border_style="cyan")
    perf_table.add_column("Operation",   style="bold")
    perf_table.add_column("Time",        style="cyan")

    perf_table.add_row("Enrollment / model training",   f"{(enroll_time_ms + train_time_ms):.1f} ms")
    perf_table.add_row("Per-window scoring latency",    f"{score_per_window_us:.1f} µs")
    perf_table.add_row("Enrollment vectors used",       str(len(train_vectors)))
    perf_table.add_row("OCSVM support vectors",         str(ocsvm.summary()["n_support_vectors"]))
    perf_table.add_row("Feature dimensions",            "6")
    console.print(perf_table)
    console.print()

    # Final verdict
    if eer < 0.10:
        verdict = f"[bold green]EXCELLENT[/bold green] — EER {eer*100:.2f}% is below 10%"
    elif eer < 0.20:
        verdict = f"[bold yellow]GOOD[/bold yellow]      — EER {eer*100:.2f}% is below 20%"
    else:
        verdict = f"[bold red]NEEDS WORK[/bold red] — EER {eer*100:.2f}% exceeds 20%"

    console.print(
        Panel.fit(
            f"  Overall System Assessment: {verdict}\n\n"
            f"  EER {eer*100:.2f}% means the system correctly distinguishes\n"
            f"  legitimate users from impostors at the equal-error operating point.  ",
            title="Evaluation Complete",
            border_style="cyan",
        )
    )

if __name__ == "__main__":
    main()
