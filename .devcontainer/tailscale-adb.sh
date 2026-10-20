#!/usr/bin/env bash
# Helper script to manage Tailscale and Wireless ADB inside Codespaces / Dev Containers

set -e

CMD="${1:-help}"

print_header() {
    echo "=================================================="
    echo "       PuKey - Tailscale Wireless ADB Helper      "
    echo "=================================================="
}

ensure_tun() {
    if [ ! -c /dev/net/tun ]; then
        echo "[i] Creating /dev/net/tun device..."
        sudo mkdir -p /dev/net
        sudo mknod /dev/net/tun c 10 200 || true
        sudo chmod 666 /dev/net/tun || true
    fi
}

case "$CMD" in
    up)
        print_header
        ensure_tun
        
        # Ensure tailscaled service is running
        if ! systemctl is-active --quiet tailscaled; then
            echo "[i] Starting tailscaled service via systemd..."
            sudo systemctl start tailscaled
        fi

        if [ -n "$TS_AUTH_KEY" ]; then
            echo "[*] Authenticating with Tailscale using \$TS_AUTH_KEY..."
            sudo tailscale up --authkey="$TS_AUTH_KEY" --accept-routes --hostname="pukey-codespace" --operator=vscode
        else
            echo "[*] Starting Tailscale interactive login..."
            echo "[!] Open the URL displayed below in your browser to authenticate:"
            sudo tailscale up --accept-routes --hostname="pukey-codespace" --operator=vscode
        fi

        echo "[✓] Tailscale is connected!"
        tailscale status
        ;;

    status)
        print_header
        echo "--> Systemd Tailscaled Service:"
        systemctl status tailscaled --no-pager || true
        echo ""
        echo "--> Tailscale Network Status:"
        tailscale status || echo "[!] Tailscale is not logged in. Run: tailscale-adb up"
        echo ""
        echo "--> ADB Connected Devices:"
        adb devices -l
        ;;

    connect)
        TARGET="$2"
        PORT="${3:-5555}"

        if [ -z "$TARGET" ]; then
            echo "Usage: tailscale-adb connect <phone-tailscale-ip-or-name> [port]"
            echo "Example: tailscale-adb connect 100.85.20.14"
            exit 1
        fi

        print_header
        echo "[*] Connecting to $TARGET on port $PORT..."
        adb connect "$TARGET:$PORT"

        echo "[*] Current ADB Devices:"
        adb devices -l
        echo ""
        echo "[Tip] If the device says 'unauthorized', look at your phone screen and tap 'Always allow from this computer'."
        ;;

    disconnect)
        TARGET="$2"
        PORT="${3:-5555}"

        if [ -z "$TARGET" ]; then
            echo "Usage: tailscale-adb disconnect <phone-tailscale-ip-or-name> [port]"
            exit 1
        fi

        print_header
        echo "[*] Disconnecting $TARGET:$PORT..."
        adb disconnect "$TARGET:$PORT"
        adb devices -l
        ;;

    devices)
        print_header
        echo "--> Connected ADB Devices:"
        adb devices -l
        echo ""
        echo "--> Active Tailscale Peers:"
        tailscale status || true
        ;;

    help|*)
        print_header
        echo "Commands:"
        echo "  tailscale-adb up                      Authenticate and join Tailscale"
        echo "  tailscale-adb connect <ip> [port]     Connect to Android device via Wireless ADB (default port: 5555)"
        echo "  tailscale-adb disconnect <ip> [port]  Disconnect ADB session"
        echo "  tailscale-adb status                  Check status of Tailscale daemon and ADB"
        echo "  tailscale-adb devices                 List ADB devices and Tailscale network peers"
        echo "  tailscale-adb help                    Show this help message"
        echo ""
        echo "Quick Workflow:"
        echo "  1. On your phone: Open Tailscale app and connect."
        echo "  2. On your phone: Enable Developer Options -> Wireless Debugging (or run 'adb tcpip 5555')."
        echo "  3. In Codespaces: run 'tailscale-adb up'"
        echo "  4. In Codespaces: run 'tailscale-adb connect <phone-tailscale-ip>'"
        echo "  5. Build & install: './gradlew installDebug'"
        ;;
esac
