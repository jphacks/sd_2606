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
sleep 2
curl -fsS http://127.0.0.1:8606/health && echo
