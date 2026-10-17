#!/usr/bin/env bash
# 初回だけ実行: venv を作り、systemd ユニットを配置して起動する。
#   bash backend/deploy/pi/install.sh
set -euo pipefail

BACKEND=$(cd "$(dirname "$0")/../.." && pwd)
cd "$BACKEND"
[ -d .venv ] || python3 -m venv .venv
.venv/bin/pip install --quiet --upgrade -r requirements.txt
[ -f .env ] || { cp .env.example .env; chmod 600 .env; echo "created $BACKEND/.env (ANTHROPIC_API_KEY を設定してください)"; }

sed -e "s|__USER__|$(id -un)|g" -e "s|__BACKEND__|$BACKEND|g" deploy/pi/sd2606-api.service \
  | sudo tee /etc/systemd/system/sd2606-api.service > /dev/null
sudo systemctl daemon-reload
sudo systemctl enable --now sd2606-api
# Pi では起動に数秒かかるので待つ
for _ in $(seq 30); do curl -fsS http://127.0.0.1:8606/health 2>/dev/null && echo && exit 0; sleep 1; done
echo "API が起動しません: journalctl -u sd2606-api -n 50" >&2; exit 1
