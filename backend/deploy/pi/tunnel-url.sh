#!/usr/bin/env bash
# 現在の Quick Tunnel の URL を表示する（最大60秒待つ）。
#   bash backend/deploy/pi/tunnel-url.sh
set -euo pipefail

for _ in $(seq 60); do
  since=$(systemctl show -p ActiveEnterTimestamp --value sd2606-tunnel)
  url=$(sudo journalctl -u sd2606-tunnel --since "$since" --no-pager -o cat 2>/dev/null \
    | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1 || true)
  if [ -n "$url" ]; then
    echo "$url/"
    exit 0
  fi
  sleep 1
done
echo "URL が見つかりません: journalctl -u sd2606-tunnel -n 50" >&2
exit 1
