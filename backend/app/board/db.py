"""掲示板の保存（SQLite、標準ライブラリだけ）。

投稿の本文は DB にだけ置き、リポジトリには入れない（public なので）。サンプル投稿は data/board_samples.yaml から入れる。
"""

import hashlib
import hmac
import logging
import secrets
import sqlite3
import statistics
import threading
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from app.board.definitions import load_samples
from app.board.schemas import NewPost, Post, Summary
from app.config import settings

log = logging.getLogger(__name__)

SCHEMA = """
CREATE TABLE IF NOT EXISTS posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    age_band TEXT,
    occupation TEXT,
    happened_ym TEXT,
    difficulty INTEGER,
    minutes INTEGER,
    helpful INTEGER NOT NULL DEFAULT 0,
    reports INTEGER NOT NULL DEFAULT 0,
    hidden INTEGER NOT NULL DEFAULT 0,
    sample_key TEXT UNIQUE,              -- サンプル投稿だけ（board_samples.yaml の key）
    delete_token_hash TEXT,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS post_tags (
    post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag_id TEXT NOT NULL,
    PRIMARY KEY (post_id, tag_id)
);
CREATE INDEX IF NOT EXISTS post_tags_tag ON post_tags(tag_id);
-- 「役に立った」と通報の重複を防ぐ。client_id はアプリのインストールごとのランダムな ID
CREATE TABLE IF NOT EXISTS votes (
    post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    client_id TEXT NOT NULL,
    type TEXT NOT NULL,
    PRIMARY KEY (post_id, client_id, type)
);
"""

_ready: set[str] = set()
_ready_lock = threading.Lock()


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    """リクエストごとに開く。初回だけ表を作り、サンプル投稿を入れる。"""
    path = settings.board_db_path
    conn = sqlite3.connect(path, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        if path not in _ready:
            with _ready_lock:
                if path not in _ready:
                    _init(conn)
                    _ready.add(path)
        yield conn
        conn.commit()
    finally:
        conn.close()


def _init(conn: sqlite3.Connection) -> None:
    Path(settings.board_db_path).parent.mkdir(parents=True, exist_ok=True)
    conn.execute("PRAGMA journal_mode = WAL")
    conn.executescript(SCHEMA)
    added = 0
    for s in load_samples():
        if conn.execute("SELECT 1 FROM posts WHERE sample_key = ?", (s["key"],)).fetchone():
            continue
        post = NewPost.model_validate({k: v for k, v in s.items() if k != "key"})
        _insert(conn, post, sample_key=s["key"])
        added += 1
    conn.commit()
    log.info("board db: %s（サンプルを %d 件追加）", settings.board_db_path, added)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def _insert(conn: sqlite3.Connection, post: NewPost, sample_key: str | None = None, token_hash: str | None = None) -> int:
    cur = conn.execute(
        """INSERT INTO posts (kind, title, body, age_band, occupation, happened_ym, difficulty, minutes,
                              sample_key, delete_token_hash, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        (post.kind, post.title.strip(), post.body.strip(), post.age_band, post.occupation, post.happened_ym,
         post.difficulty, post.minutes, sample_key, token_hash, _now()),
    )
    post_id = cur.lastrowid
    conn.executemany("INSERT OR IGNORE INTO post_tags (post_id, tag_id) VALUES (?, ?)", [(post_id, t) for t in post.tags])
    return post_id


def create(post: NewPost) -> tuple[Post, str]:
    token = secrets.token_urlsafe(24)
    with connect() as conn:
        post_id = _insert(conn, post, token_hash=_hash(token))
        return _fetch(conn, [post_id])[0], token


def _fetch(conn: sqlite3.Connection, ids: list[int]) -> list[Post]:
    """id の順に Post を作る（タグつき）。"""
    if not ids:
        return []
    marks = ",".join("?" * len(ids))
    rows = {r["id"]: r for r in conn.execute(f"SELECT * FROM posts WHERE id IN ({marks})", ids)}
    tags: dict[int, list[str]] = {}
    for r in conn.execute(f"SELECT post_id, tag_id FROM post_tags WHERE post_id IN ({marks}) ORDER BY rowid", ids):
        tags.setdefault(r["post_id"], []).append(r["tag_id"])
    return [
        Post(
            id=r["id"], kind=r["kind"], title=r["title"], body=r["body"], tags=tags.get(r["id"], []),
            age_band=r["age_band"], occupation=r["occupation"], happened_ym=r["happened_ym"],
            difficulty=r["difficulty"], minutes=r["minutes"], helpful=r["helpful"],
            sample=r["sample_key"] is not None, created_at=r["created_at"],
        )
        for i in ids
        if (r := rows.get(i)) is not None
    ]


def _where(tags: list[str] | None, kind: str | None) -> tuple[str, list]:
    sql, args = "p.hidden = 0", []
    if kind:
        sql += " AND p.kind = ?"
        args.append(kind)
    if tags is not None:
        if not tags:
            return "0", []
        sql += f" AND EXISTS (SELECT 1 FROM post_tags t WHERE t.post_id = p.id AND t.tag_id IN ({','.join('?' * len(tags))}))"
        args += tags
    return sql, args


def list_posts(tags: list[str] | None, kind: str | None, sort: str, limit: int, offset: int) -> tuple[list[Post], bool]:
    """tags が None なら絞り込まない。どれかのタグが付いた投稿を返す。2つ目は続きがあるか。"""
    where, args = _where(tags, kind)
    order = "p.helpful DESC, p.id DESC" if sort == "helpful" else "p.id DESC"
    with connect() as conn:
        ids = [r[0] for r in conn.execute(f"SELECT p.id FROM posts p WHERE {where} ORDER BY {order} LIMIT ? OFFSET ?",
                                          [*args, limit + 1, offset])]
        return _fetch(conn, ids[:limit]), len(ids) > limit


def get(post_id: int) -> Post | None:
    with connect() as conn:
        hidden = conn.execute("SELECT hidden FROM posts WHERE id = ?", (post_id,)).fetchone()
        if hidden is None or hidden[0]:
            return None
        return _fetch(conn, [post_id])[0]


def summary(tags: list[str]) -> Summary:
    where, args = _where(tags, None)
    with connect() as conn:
        rows = conn.execute(f"SELECT p.kind, p.difficulty, p.minutes FROM posts p WHERE {where}", args).fetchall()
    difficulties = [r["difficulty"] for r in rows if r["difficulty"] is not None]
    minutes = [r["minutes"] for r in rows if r["minutes"] is not None]
    kinds: dict[str, int] = {}
    for r in rows:
        kinds[r["kind"]] = kinds.get(r["kind"], 0) + 1
    return Summary(
        count=len(rows),
        avg_difficulty=round(statistics.mean(difficulties), 1) if difficulties else None,
        median_minutes=int(statistics.median_low(minutes)) if minutes else None,
        kinds=kinds,
    )


def count_by_tag() -> dict[str, set[int]]:
    """タグごとの（表示中の）投稿の id。手続きごとの件数を数えるのに使う。"""
    with connect() as conn:
        rows = conn.execute("SELECT t.tag_id, t.post_id FROM post_tags t JOIN posts p ON p.id = t.post_id WHERE p.hidden = 0")
        result: dict[str, set[int]] = {}
        for r in rows:
            result.setdefault(r["tag_id"], set()).add(r["post_id"])
        return result


def _exists(conn: sqlite3.Connection, post_id: int) -> bool:
    return conn.execute("SELECT 1 FROM posts WHERE id = ? AND hidden = 0", (post_id,)).fetchone() is not None


def set_helpful(post_id: int, client_id: str, on: bool) -> int | None:
    """「役に立った」を付ける／外す。投稿が無ければ None。"""
    with connect() as conn:
        if not _exists(conn, post_id):
            return None
        if on:
            changed = conn.execute("INSERT OR IGNORE INTO votes VALUES (?, ?, 'helpful')", (post_id, client_id)).rowcount
        else:
            changed = -conn.execute("DELETE FROM votes WHERE post_id = ? AND client_id = ? AND type = 'helpful'",
                                    (post_id, client_id)).rowcount
        if changed:
            conn.execute("UPDATE posts SET helpful = MAX(0, helpful + ?) WHERE id = ?", (changed, post_id))
        return conn.execute("SELECT helpful FROM posts WHERE id = ?", (post_id,)).fetchone()[0]


def report(post_id: int, client_id: str) -> bool:
    """通報する。決まった件数に達したら非表示にする。投稿が無ければ False。"""
    with connect() as conn:
        if not _exists(conn, post_id):
            return False
        if conn.execute("INSERT OR IGNORE INTO votes VALUES (?, ?, 'report')", (post_id, client_id)).rowcount:
            conn.execute("UPDATE posts SET reports = reports + 1 WHERE id = ?", (post_id,))
            conn.execute("UPDATE posts SET hidden = 1 WHERE id = ? AND reports >= ?", (post_id, settings.board_report_hide))
        return True


def delete(post_id: int, token: str) -> bool:
    """削除用トークンが合えば消す。"""
    with connect() as conn:
        row = conn.execute("SELECT delete_token_hash FROM posts WHERE id = ?", (post_id,)).fetchone()
        if row is None or row[0] is None or not hmac.compare_digest(row[0], _hash(token)):
            return False
        conn.execute("DELETE FROM posts WHERE id = ?", (post_id,))
        return True


def set_hidden(post_id: int, hidden: bool) -> bool:
    """管理用（app.board.admin から使う）。"""
    with connect() as conn:
        return conn.execute("UPDATE posts SET hidden = ? WHERE id = ?", (int(hidden), post_id)).rowcount > 0
