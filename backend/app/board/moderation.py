"""投稿のチェック（個人情報らしい文字列・リンク）と、送信元ごとの回数制限。"""

import re
import threading
import time
import unicodedata
from collections import defaultdict, deque

# 公的機関のページへのリンクだけ許す（宣伝・スパム対策）
ALLOWED_LINK = re.compile(r"https?://[\w.-]*\.(go\.jp|lg\.jp)(/\S*)?", re.ASCII)

PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+", re.ASCII), "メールアドレス"),
    (re.compile(r"(?<![\d-])0\d{1,4}-?\d{1,4}-?\d{3,4}(?![\d-])"), "電話番号"),
    (re.compile(r"(?<!\d)\d{4}[ -]?\d{4}[ -]?\d{4}(?!\d)"), "マイナンバーなどの12桁の番号"),
    (re.compile(r"〒\s*\d{3}-?\d{4}|(?<![\d-])\d{3}-\d{4}(?![\d-])"), "郵便番号"),
    (re.compile(r"https?://\S+"), "リンク（公的機関のページ以外）"),
]


def find_personal_info(text: str) -> str | None:
    """個人情報らしいもの（電話番号・メールアドレスなど）が入っていれば、その種類を返す。"""
    normalized = ALLOWED_LINK.sub("", unicodedata.normalize("NFKC", text))
    for pattern, name in PATTERNS:
        for m in pattern.finditer(normalized):
            if name == "電話番号" and not 10 <= sum(c.isdigit() for c in m.group()) <= 11:
                continue
            return name
    return None


class RateLimiter:
    """直近 window 秒の回数を送信元ごとに数える（メモリ上だけ。IP は保存しない）。"""

    def __init__(self, limit: int, window_sec: float):
        self.limit = limit
        self.window = window_sec
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        with self._lock:
            hits = self._hits[key]
            while hits and now - hits[0] > self.window:
                hits.popleft()
            if len(hits) >= self.limit:
                return False
            hits.append(now)
            return True

    def reset(self) -> None:
        with self._lock:
            self._hits.clear()
