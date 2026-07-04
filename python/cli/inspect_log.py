"""
TODO!: fix this shit kizu -nes
Usage
-----
    python -m cli.inspect_log                   # show all entries
    python -m cli.inspect_log --user alice      # filter by user
    python -m cli.inspect_log --last 20         # show last N entries
    python -m cli.inspect_log --clear --user alice  # wipe user's entries
"""

import argparse
import sys
from datetime import datetime

from rich.console import Console
from rich.panel import Panel
from rich.table import Table

from engine.logger import SecurityLogger

console = Console()

EVENT_STYLES = {
    "ENROLLMENT":    "cyan",
    "VERIFICATION":  "green",
    "ANOMALY":       "yellow",
    "SESSION_START": "blue",
    "SESSION_BLOCK": "bold red",
    "SESSION_STEP_UP": "bold orange3",
}

def main():
    parser = argparse.ArgumentParser(
        description="Decrypt and inspect the encrypted security event log."
    )
    parser.add_argument("--user",  default=None,  help="Filter entries by user ID")
    parser.add_argument("--last",  type=int,       help="Show only the last N entries")
    parser.add_argument("--clear", action="store_true",
                        help="Delete log entries (filtered by --user if provided)")
    args = parser.parse_args()

    logger = SecurityLogger()

    if args.clear:
        confirm = input(
            f"⚠ This will delete "
            f"{'ALL' if not args.user else f\"'{args.user}'s\"} log entries. "
            f"Type 'yes' to confirm: "
        )
        if confirm.strip().lower() == "yes":
            logger.clear(args.user)
            console.print("[green]✔ Log entries deleted.[/green]")
        else:
            console.print("[yellow]Cancelled.[/yellow]")
        return

    entries = logger.read_all(user_id=args.user)

    if not entries:
        console.print(
            Panel.fit(
                "[yellow]No log entries found.[/yellow]\n\n"
                "Run an enrollment or verification session first.",
                title="Security Log",
                border_style="yellow",
            )
        )
        return

    if args.last:
        entries = entries[-args.last:]

    # Summary counts
    event_counts: dict = {}
    for e in entries:
        event_counts[e["event_type"]] = event_counts.get(e["event_type"], 0) + 1

    console.print(
        Panel.fit(
            f"Total entries: [cyan]{logger.count(args.user)}[/cyan]  "
            f"{'(filtered by user: ' + args.user + ')' if args.user else ''}  "
            f"| Showing: [cyan]{len(entries)}[/cyan]",
            title="[bold]Security Log — Encrypted SQLite[/bold]",
            border_style="cyan",
        )
    )
    console.print()

    # Main log table
    table = Table(border_style="dim", show_lines=True)
    table.add_column("ID",         style="dim",    width=5)
    table.add_column("User",       style="cyan",   width=12)
    table.add_column("Timestamp",  style="dim",    width=25)
    table.add_column("Event",      width=18)
    table.add_column("Risk",       width=8)
    table.add_column("Action",     width=15)
    table.add_column("Payload",    width=45)

    for entry in entries:
        event_type = entry["event_type"]
        style = EVENT_STYLES.get(event_type, "white")

        risk_str   = (
            f"{entry['risk_score']:.3f}"
            if entry["risk_score"] is not None else "—"
        )
        action_str = entry["action"] or "—"
        payload    = entry.get("payload", {})

        # Format payload for display
        payload_str = "  ".join(
            f"{k}: {v}" for k, v in payload.items()
            if k != "error"
        )[:60]
        if "error" in payload:
            payload_str = "[red]DECRYPTION FAILED[/red]"

        # Format timestamp nicely
        try:
            dt = datetime.fromisoformat(entry["timestamp"].replace("Z", "+00:00"))
            ts_str = dt.strftime("%Y-%m-%d %H:%M:%S")
        except Exception:
            ts_str = entry["timestamp"][:19]

        table.add_row(
            str(entry["id"]),
            entry["user_id"],
            ts_str,
            f"[{style}]{event_type}[/{style}]",
            risk_str,
            action_str,
            payload_str,
        )

    console.print(table)

    # Summary footer
    console.print()
    summary_table = Table(title="Event Type Breakdown", border_style="dim", show_header=True)
    summary_table.add_column("Event Type", style="bold")
    summary_table.add_column("Count",      style="cyan")
    for etype, count in sorted(event_counts.items()):
        style = EVENT_STYLES.get(etype, "white")
        summary_table.add_row(f"[{style}]{etype}[/{style}]", str(count))
    console.print(summary_table)

if __name__ == "__main__":
    main()
