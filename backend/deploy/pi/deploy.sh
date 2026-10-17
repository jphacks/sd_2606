#!/usr/bin/env bash
# 最新の main を取り込み、API を再起動する（初回は install.sh を使う）。
#   ssh <pi> 'bash ~/sd_2606/backend/deploy/pi/deploy.sh'
set -euo pipefail

REPO=$(cd "$(dirname "$0")/../../.." && pwd)
cd "$REPO"
git fetch --quiet origin
git checkout --quiet main
git reset --quiet --hard origin/main

cd backend
.venv/bin/pip install --quiet --upgrade -r requirements.txt

sudo systemctl restart sd2606-api
# Pi では起動に数秒かかるので待つ
for _ in $(seq 30); do curl -fsS http://127.0.0.1:8606/health 2>/dev/null && echo && exit 0; sleep 1; done
echo "API が起動しません: journalctl -u sd2606-api -n 50" >&2; exit 1
