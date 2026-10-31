#!/usr/bin/env bash
# Connect to the tablet over wireless ADB (Android 13).
#
#   scripts/connect.sh          reuse debug.properties, fall back to mDNS, then prompt
#   scripts/connect.sh --pair   force a fresh pairing
#
# The wireless-debugging port and pairing code change after every reboot or toggle, so on a
# failed connection the script asks for the new values and writes them back to debug.properties.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT/debug.properties"
[[ -f "$PROPS" ]] || cp "$ROOT/debug.properties.example" "$PROPS"

prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r'; }
set_prop() {
  local tmp; tmp="$(mktemp)"
  awk -v k="$1" -v v="$2" -F= 'BEGIN{done=0} $1==k {print k"="v; done=1; next} {print} END{if(!done) print k"="v}' "$PROPS" > "$tmp"
  mv "$tmp" "$PROPS"
}
is_placeholder() { [[ -z "$1" || "$1" == \[* ]]; }

ADB="adb"
if [[ -f "$ROOT/local.properties" ]]; then
  SDK="$(grep -E '^sdk.dir=' "$ROOT/local.properties" | cut -d= -f2- || true)"
  [[ -x "$SDK/platform-tools/adb" ]] && ADB="$SDK/platform-tools/adb"
fi
command -v "$ADB" >/dev/null 2>&1 || { echo "adb not found. Install Android platform-tools." >&2; exit 1; }

IP="$(prop DEVICE_IP)"
is_placeholder "$IP" && { echo "DEVICE_IP missing in debug.properties" >&2; exit 1; }

connected() { [[ "$("$ADB" -s "$IP:$1" get-state 2>/dev/null || true)" == "device" ]]; }

try_connect() {
  local port="$1"
  is_placeholder "$port" && return 1
  echo "→ adb connect $IP:$port"
  "$ADB" connect "$IP:$port" 2>&1 | sed 's/^/  /' || true
  sleep 1
  if connected "$port"; then set_prop ADB_PORT "$port"; return 0; fi
  return 1
}

done_msg() {
  echo "✓ Connected to $IP:$1"
  echo "  Next: ./gradlew installAndLaunch   (or setDefaultHome, triggerMode -Pmode=drive, logcat)"
  exit 0
}

if [[ "${1:-}" != "--pair" ]]; then
  # 1) Last known port.
  PORT="$(prop ADB_PORT)"
  try_connect "$PORT" && done_msg "$PORT"

  # 2) mDNS discovery (works once the computer has been paired before).
  echo "→ Looking for the tablet via mDNS…"
  MDNS_PORT="$("$ADB" mdns services 2>/dev/null | grep '_adb-tls-connect' | grep -oE "$IP:[0-9]+" | head -1 | cut -d: -f2 || true)"
  if [[ -n "$MDNS_PORT" ]] && try_connect "$MDNS_PORT"; then done_msg "$MDNS_PORT"; fi
  echo "✗ Could not connect with the saved port. The port changes after every reboot or Wireless-debugging toggle."
fi

# 3) Pair + connect with fresh values.
cat <<'TXT'

On the tablet: Settings › Developer options › Wireless debugging
  • "Pair device with pairing code" shows the PAIRING port and 6-digit code
  • The main Wireless debugging screen shows "IP address & Port" (that is the ADB port)
TXT
read -r -p "Pairing port: " PAIR_PORT
read -r -p "Pairing code: " PAIR_CODE
set_prop PAIRING_PORT "$PAIR_PORT"
set_prop PAIRING_CODE "$PAIR_CODE"
echo "→ adb pair $IP:$PAIR_PORT ******"
"$ADB" pair "$IP:$PAIR_PORT" "$PAIR_CODE"

read -r -p "ADB port (from 'IP address & Port'): " ADB_PORT
try_connect "$ADB_PORT" && done_msg "$ADB_PORT"
echo "✗ Still not connected. Check that the tablet and this computer are on the same Wi-Fi and the port is current." >&2
exit 1
