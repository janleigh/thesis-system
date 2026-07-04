"""
Usage
-----
    python -m cli.verify --user alice
    python -m cli.verify --user alice --window 20    # window size override
Press ESC to end the session.
"""

import argparse
import sys
import time

from rich.console import Console
from rich.live import Live
from rich.panel import Panel
from rich.table import Table
from rich.text import Text

from collector.keyboard_listener import KeyboardListener
from engine.feature_extractor import FeatureExtractor
from engine.logger import SecurityLogger
from engine.profile_manager import ProfileManager
from engine.risk_scorer import Action, RiskScorer

console = Console()

# Action → color mapping for terminal output
ACTION_STYLES = {
    Action.ALLOW:         "bold green",
    Action.WARN:          "bold yellow",
    Action.STEP_UP_AUTH:  "bold orange3",
    Action.BLOCK_SESSION: "bold red",
}

ACTION_ICONS = {
    Action.ALLOW:         "✔",
    Action.WARN:          "⚠",
    Action.STEP_UP_AUTH:  "🔒",
    Action.BLOCK_SESSION: "🚫",
}

def main():
    parser = argparse.ArgumentParser(
        description="Run a live verification session for an enrolled user."
    )
    parser.add_argument("--user",   required=True, help="User ID to verify")
    parser.add_argument("--window", type=int, default=30,
                        help="Number of keystrokes per verification window (default 30)")
    args = parser.parse_args()

    pm = ProfileManager()
    profile = pm.get_profile(args.user)

    if not profile or not profile.is_ready:
        console.print(
            f"[red]✘ User '[bold]{args.user}[/bold]' is not enrolled or enrollment is incomplete.[/red]\n"
            f"  Run: [bold]python -m cli.enroll --user {args.user}[/bold]"
        )
        sys.exit(1)

    ocsvm  = pm.get_ocsvm(args.user)
    mahal  = pm.get_mahalanobis(args.user)
    scorer = RiskScorer()
    extractor = FeatureExtractor()
    logger = SecurityLogger()

    # Log session start
    logger.log(
        user_id=args.user,
        event_type="SESSION_START",
        payload={"window_size": args.window},
    )

    console.rule(f"[bold cyan]Live Verification — {args.user}[/bold cyan]")
    console.print(
        f"\n[bold]Instructions[/bold]\n"
        f"  • Type freely — the system scores every [cyan]{args.window}[/cyan] keystrokes\n"
        f"  • Risk decisions appear in real-time\n"
        f"  • Press [bold red]ESC[/bold red] to end the session\n"
    )
    console.print("[dim]Starting listener in 1 second...[/dim]\n")
    time.sleep(1)

    session_results = []
    window_num      = [0]
    blocked         = [False]

    def on_window(events, keystroke_count):
        if blocked[0]:
            return

        fv = extractor.extract(events)
        if not fv or not fv.is_valid():
            return

        window_num[0] += 1
        stat_arr    = fv.to_stat_array()
        ocsvm_score = ocsvm.anomaly_score(stat_arr)
        mahal_score = mahal.normalized_score(stat_arr)
        decision    = scorer.score(ocsvm_score, mahal_score, keystroke_count)

        session_results.append(decision)

        # Build output row
        style = ACTION_STYLES[decision.action]
        icon  = ACTION_ICONS[decision.action]

        console.print(
            f"  Window {window_num[0]:>3} │ "
            f"OCSVM: [cyan]{ocsvm_score:.3f}[/cyan]  "
            f"Mahal: [cyan]{mahal_score:.3f}[/cyan]  "
            f"Risk: [cyan]{decision.composite_score:.3f}[/cyan]  "
            f"[{style}]{icon} {decision.action.value}[/{style}]"
        )

        # Log to encrypted SQLite
        logger.log(
            user_id=args.user,
            event_type="VERIFICATION" if not decision.is_anomaly else "ANOMALY",
            risk_score=decision.composite_score,
            action=decision.action.value,
            payload={
                "window":       window_num[0],
                "ocsvm_score":  round(ocsvm_score, 4),
                "mahal_score":  round(mahal_score, 4),
                "keystroke_n":  keystroke_count,
            },
        )

        # Handle blocking actions
        if decision.action == Action.BLOCK_SESSION:
            blocked[0] = True
            listener_ref[0].stop()
            console.print()
            logger.log(
                user_id=args.user,
                event_type="SESSION_BLOCK",
                payload={"reason": "risk_score_exceeded_block_threshold"},
            )

        elif decision.action == Action.STEP_UP_AUTH:
            console.print(
                "\n  [bold orange3]⚡ Step-up authentication required![/bold orange3]\n"
            )

    listener_ref = [None]
    listener = KeyboardListener(
        window_size=args.window,
        window_timeout=30.0,
        on_window=on_window,
    )
    listener_ref[0] = listener

    listener.start()
    listener.join()

    # Session summary
    console.print()
    _print_session_summary(args.user, session_results, blocked[0])

def _print_session_summary(user_id: str, results: list, blocked: bool) -> None:
    if not results:
        console.print("[yellow]No verification windows were processed.[/yellow]")
        return

    scores  = [r.composite_score for r in results]
    actions = [r.action for r in results]

    table = Table(title=f"Session Summary — {user_id}", border_style="cyan")
    table.add_column("Metric",       style="bold")
    table.add_column("Value",        style="cyan")

    table.add_row("Windows evaluated",   str(len(results)))
    table.add_row("Avg risk score",      f"{sum(scores)/len(scores):.3f}")
    table.add_row("Max risk score",      f"{max(scores):.3f}")
    table.add_row("ALLOW windows",       str(actions.count(Action.ALLOW)))
    table.add_row("WARN windows",        str(actions.count(Action.WARN)))
    table.add_row("STEP_UP_AUTH",        str(actions.count(Action.STEP_UP_AUTH)))
    table.add_row("BLOCK_SESSION",       str(actions.count(Action.BLOCK_SESSION)))
    table.add_row("Session terminated",  "[red]YES[/red]" if blocked else "[green]NO[/green]")

    console.print(table)

if __name__ == "__main__":
    main()
