#!/usr/bin/env bash
# 最新の main（または指定したブランチ）を取り込み、API を再起動する（初回は install.sh を使う）。
#   ssh <pi> 'bash ~/sd_2606/backend/deploy/pi/deploy.sh'              # main
#   ssh <pi> 'bash ~/sd_2606/backend/deploy/pi/deploy.sh feat/board'   # 動作確認のためにブランチを出す
# ローカルの変更は破棄する。掲示板の DB は /var/lib/sd2606 にあるので消えない。
set -euo pipefail

BRANCH=${1:-main}
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
cd "$REPO"
git fetch --quiet origin
git reset --quiet --hard
git checkout --quiet -B "$BRANCH" "origin/$BRANCH"
echo "deploy: $BRANCH $(git log -1 --format='%h %s')"

cd backend
.venv/bin/pip install --quiet --upgrade -r requirements.txt

# systemd ユニットが変わっていたら置き直す
UNIT=/etc/systemd/system/sd2606-api.service
NEW=$(sed -e "s|__USER__|$(id -un)|g" -e "s|__BACKEND__|$PWD|g" deploy/pi/sd2606-api.service)
if [ "$NEW" != "$(cat "$UNIT" 2>/dev/null)" ]; then
  echo "$NEW" | sudo tee "$UNIT" > /dev/null
  sudo systemctl daemon-reload
  echo "updated $UNIT"
fi

sudo systemctl restart sd2606-api
# Pi では起動に数秒かかるので待つ
for _ in $(seq 30); do curl -fsS http://127.0.0.1:8606/health 2>/dev/null && echo && exit 0; sleep 1; done
echo "API が起動しません: journalctl -u sd2606-api -n 50" >&2; exit 1
