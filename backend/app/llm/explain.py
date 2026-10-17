"""ルールで判定した手続きに、Claude で個別の補足説明を付ける。

API キーが無い・失敗した・呼び出し上限に達した場合は None を返し、ルール判定の結果だけで動く。
"""

import hashlib
import json
import logging
import time
from collections import OrderedDict, deque

import anthropic
from pydantic import BaseModel

from app.config import settings
from app.schemas import Recommendation, UserProfile

log = logging.getLogger(__name__)

SYSTEM_PROMPT = """\
あなたは日本の税金・社会保険・年金の手続きをやさしく案内するアシスタントです。
ユーザーのプロフィールと、ルールで該当と判定された手続きの一覧が与えられます。
- overall_advice: どの順番で何から手をつけるべきかを、2〜3文で具体的に伝えてください。
- notes: 各手続きについて、このユーザーの状況に即した注意点や得になる点を1〜2文で書いてください。procedure_id は入力のものをそのまま使ってください。
金額や期限は入力に書かれている範囲で述べ、制度の細かい条件で断定できない点は「窓口で確認を」と添えてください。専門用語は避け、中学生にも分かる言葉で書いてください。"""


class ProcedureNote(BaseModel):
    procedure_id: str
    note: str


class Explanation(BaseModel):
    overall_advice: str
    notes: list[ProcedureNote]


class _RateLimiter:
    def __init__(self, per_minute: int) -> None:
        self.per_minute = per_minute
        self.calls: deque[float] = deque()

    def allow(self) -> bool:
        now = time.monotonic()
        while self.calls and now - self.calls[0] > 60:
            self.calls.popleft()
        if len(self.calls) >= self.per_minute:
            return False
        self.calls.append(now)
        return True


_limiter = _RateLimiter(settings.llm_calls_per_minute)
_cache: OrderedDict[str, Explanation] = OrderedDict()
_CACHE_SIZE = 256
_client: anthropic.AsyncAnthropic | None = None


def _get_client() -> anthropic.AsyncAnthropic:
    global _client
    if _client is None:
        _client = anthropic.AsyncAnthropic(timeout=settings.llm_timeout_sec, max_retries=1)
    return _client


async def explain(profile: UserProfile, items: list[Recommendation]) -> Explanation | None:
    if not settings.llm_enabled or not items:
        return None

    payload = json.dumps(
        {
            "profile": profile.model_dump(),
            "procedures": [
                {"procedure_id": i.procedure_id, "title": i.title, "reasons": i.reasons, "deadline": i.deadline}
                for i in items
            ],
        },
        ensure_ascii=False,
        sort_keys=True,
    )
    key = hashlib.sha256(payload.encode()).hexdigest()
    if key in _cache:
        _cache.move_to_end(key)
        return _cache[key]
    if not _limiter.allow():
        log.warning("LLM rate limit reached; skipping explanation")
        return None

    try:
        response = await _get_client().beta.messages.parse(
            model=settings.claude_model,
            max_tokens=4000,
            output_config={"effort": "low"},
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": payload}],
            output_format=Explanation,
        )
    except anthropic.APIError:
        log.exception("Claude API call failed")
        return None

    if response.stop_reason == "refusal" or response.parsed_output is None:
        log.warning("no explanation generated (stop_reason=%s)", response.stop_reason)
        return None

    _cache[key] = response.parsed_output
    if len(_cache) > _CACHE_SIZE:
        _cache.popitem(last=False)
    return response.parsed_output
