import argparse
import sys

from rich.console import Console
from rich.live import Live
from rich.panel import Panel
from rich.progress import BarColumn, Progress, SpinnerColumn, TextColumn
from rich.text import Text

from collector.keyboard_listener import KeyboardListener
from engine.feature_extractor import FeatureExtractor
from engine.logger import SecurityLogger
from engine.profile_manager import ProfileManager

console = Console()

def main():
    parser = argparse.ArgumentParser(
        description="Enroll a user into the biometric authentication system."
    )
    parser.add_argument("--user",  required=True, help="User ID / username")
    parser.add_argument("--test",  action="store_true",
                        help="Use reduced keystroke minimum (50) for quick testing")
    parser.add_argument("--reset", action="store_true",
                        help="Delete existing profile before enrolling")
    args = parser.parse_args()

    pm     = ProfileManager(testing_mode=args.test)
    logger = SecurityLogger()
    extractor = FeatureExtractor()

    if args.reset:
        pm.delete_user(args.user)
        console.print(f"[yellow]⚠ Existing profile for '{args.user}' deleted.[/yellow]")

    # check if already enrolled idk why we care
    profile = pm.get_profile(args.user)
    if profile and profile.is_ready:
        console.print(
            f"[green]✔ User '[bold]{args.user}[/bold]' is already fully enrolled.[/green]\n"
            f"  Keystroke count: {profile.keystroke_count} / {profile.min_keystrokes}\n"
            f"  Use [bold]--reset[/bold] to re-enroll."
        )
        sys.exit(0)

    min_ks = 50 if args.test else 500

    console.rule(f"[bold cyan]Enrollment — {args.user}[/bold cyan]")
    console.print(
        f"\n[bold]Instructions[/bold]\n"
        f"  • Type freely in this terminal window\n"
        f"  • Minimum keystrokes required: [cyan]{min_ks}[/cyan]\n"
        f"  • Press [bold red]ESC[/bold red] to stop at any time\n"
        f"  • For best accuracy, type varied text (sentences, not repeated characters)\n"
    )
    console.print("[dim]Starting listener in 2 seconds...[/dim]\n")

    import time
    time.sleep(2)

    progress = Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        BarColumn(),
        TextColumn("{task.completed}/{task.total} keystrokes"),
        console=console,
        transient=False,
    )
    task_id = progress.add_task("[cyan]Enrolling...", total=min_ks)

    total_ks  = [0]
    done_flag = [False]

    def on_window(events, keystroke_count):
        fv = extractor.extract(events)
        if fv:
            updated = pm.add_enrollment_window(args.user, fv)
            total_ks[0] = updated.keystroke_count
            progress.update(task_id, completed=min(updated.keystroke_count, min_ks))

            logger.log(
                user_id=args.user,
                event_type="ENROLLMENT",
                payload={
                    "window_ks":       keystroke_count,
                    "total_ks":        updated.keystroke_count,
                    "dwell_mean_ms":   round(fv.dwell_mean, 2),
                    "flight_mean_ms":  round(fv.flight_mean, 2),
                    "status":          updated.status,
                },
            )

            if updated.is_ready and not done_flag[0]:
                done_flag[0] = True
                listener_ref[0].stop()

    listener_ref = [None]
    listener = KeyboardListener(
        window_size=30,
        window_timeout=10.0,
        on_window=on_window,
    )
    listener_ref[0] = listener

    with progress:
        listener.start()
        listener.join()

    console.print()
    profile = pm.get_profile(args.user)
    if profile and profile.is_ready:
        console.print(
            Panel.fit(
                f"[bold green]✔ Enrollment Complete![/bold green]\n\n"
                f"  User           : [cyan]{args.user}[/cyan]\n"
                f"  Keystrokes     : [cyan]{profile.keystroke_count}[/cyan]\n"
                f"  Status         : [green]{profile.status}[/green]\n\n"
                f"  Models trained : OCSVM + Mahalanobis\n"
                f"  You can now run: [bold]python -m cli.verify --user {args.user}[/bold]",
                title="Enrollment",
                border_style="green",
            )
        )
    else:
        ks = profile.keystroke_count if profile else total_ks[0]
        console.print(
            Panel.fit(
                f"[yellow]⚠ Enrollment incomplete.[/yellow]\n\n"
                f"  Keystrokes captured: [cyan]{ks}[/cyan] / [cyan]{min_ks}[/cyan]\n"
                f"  Run enroll again to continue — progress is saved.",
                title="Incomplete",
                border_style="yellow",
            )
        )
        sys.exit(1)

if __name__ == "__main__":
    main()
