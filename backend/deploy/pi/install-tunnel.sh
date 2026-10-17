#!/usr/bin/env bash
# Cloudflare Quick Tunnel を systemd サービスとして配置・起動する（cloudflared は事前にインストール）。
#   bash backend/deploy/pi/install-tunnel.sh
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
command -v cloudflared >/dev/null || { echo "cloudflared が見つかりません" >&2; exit 1; }

sed -e "s|__USER__|$(id -un)|g" "$DIR/sd2606-tunnel.service" \
  | sudo tee /etc/systemd/system/sd2606-tunnel.service > /dev/null
sudo systemctl daemon-reload
sudo systemctl enable sd2606-tunnel
sudo systemctl restart sd2606-tunnel
bash "$DIR/tunnel-url.sh"
