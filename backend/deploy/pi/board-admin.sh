#!/usr/bin/env bash
# 掲示板の管理（Pi 上で実行）。投稿の一覧・非表示・表示に戻す・バックアップ。
#   bash ~/sd_2606/backend/deploy/pi/board-admin.sh list
#   bash ~/sd_2606/backend/deploy/pi/board-admin.sh hide <id>
#   bash ~/sd_2606/backend/deploy/pi/board-admin.sh backup ~/board-backup.db
set -euo pipefail
cd "$(dirname "$0")/../.."
BOARD_DB_PATH=/var/lib/sd2606/board.db exec .venv/bin/python -m app.board.admin "$@"
