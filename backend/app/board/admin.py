"""掲示板の管理（サーバー上で実行）。

    python -m app.board.admin list            # 最近の投稿（非表示も含む）
    python -m app.board.admin hide <id>       # 非表示にする
    python -m app.board.admin show <id>       # 表示に戻す
    python -m app.board.admin backup <path>   # 動かしたままコピーを取る

Pi では BOARD_DB_PATH=/var/lib/sd2606/board.db を付けて実行する（backend/deploy/pi/board-admin.sh）。
"""

import sqlite3
import sys

from app.board import db
from app.config import settings


def main(args: list[str]) -> int:
    match args:
        case ["list"]:
            with db.connect() as conn:
                for r in conn.execute("SELECT id, hidden, reports, helpful, sample_key, created_at, title FROM posts ORDER BY id DESC LIMIT 50"):
                    flags = ("非表示 " if r["hidden"] else "") + ("サンプル " if r["sample_key"] else "")
                    print(f"{r['id']:>4}  {r['created_at']}  通報{r['reports']} 役立{r['helpful']}  {flags}{r['title']}")
        case ["hide" | "show" as op, post_id]:
            ok = db.set_hidden(int(post_id), op == "hide")
            print("ok" if ok else "not found")
            return 0 if ok else 1
        case ["backup", path]:
            with db.connect() as conn, sqlite3.connect(path) as dst:
                conn.backup(dst)
            print(f"{settings.board_db_path} -> {path}")
        case _:
            print(__doc__)
            return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
