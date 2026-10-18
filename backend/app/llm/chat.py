"""AI への質問（アプリの「AIに質問」）。

アプリが送るのは、回答を表示用の文字に加工したもの（生年月日ではなく年齢など）と、端末内で判定した手続きの状態だけ。
サーバーには保存せず、ログにも本文を残さない。制度データ（手続きの YAML）はサーバー側で読み込んで渡す。

答え方の決まり:
- ユーザーの状態で答えが変わるのに、その状態が分からないとき → 場合分けで答えず、状態を1つ聞き返す（kind: question）
- ユーザーの希望で選ぶもの（学生納付特例を使うか、など）→ 条件分岐の形で答えてよい
"""

import json
import logging
from datetime import date
from typing import Literal

import anthropic
from pydantic import BaseModel, Field

from app.config import settings
from app.llm.explain import _RateLimiter, _get_client
from app.rules.engine import load_procedures
from app.rules.questions import build_catalog

log = logging.getLogger(__name__)

SYSTEM_RULES = """\
あなたは、日本の学生向けに税金・社会保険・年金の手続きを案内するアプリ「税人（ぜいんちゅ）」の中のアシスタントです。
ユーザーの情報（アプリで答えた内容）と、アプリが判定した手続きの状態、制度データ（手続きの説明・フロー・判定条件）が与えられます。
「いま見ている手続き」があれば、質問はその手続きについてのものとして扱ってください。

答え方の決まり:
1. ユーザーの情報に書いてあることは聞き返さず、その値を使って答える（例:「年収160万円なので〜」）。
2. 答えがユーザーの状態（事実）によって変わり、その状態がユーザーの情報から分からないときは、場合分けで答えない。
   kind を "question" にして、状態を確定させる質問を1つだけ text に書き、options に2〜4個の選択肢を入れる。
   その質問が「聞き返しに使える項目」のどれかに当たるなら、field にその項目名、各選択肢の value にその項目の値を入れる（当たらなければ field と value は null）。
   ユーザーが答えたら、元の質問に戻って答える。
3. ユーザーの希望や選び方で答えが変わるとき（例: 学生納付特例を使うか保険料を納めるか）は、kind を "answer" にして、
   「〜したいなら A、〜なら B」のような条件分岐で答えてよい。
4. kind が "answer" のとき、options は空にする。
5. 短く、やさしい日本語で書く。全体で300字程度まで。Markdown の見出し・表・太字は使わず、箇条書きは「・」で始めて1項目ごとに改行する。
6. 金額・期限・条件は与えられた制度データ（令和8年分・2026年の基準）の範囲で述べる。データに無いことや自治体で違うことは断定せず「窓口で確認を」と添える。
7. 税金・保険・年金・役所の手続きと関係のない質問には、このアプリでは答えられないと短く伝える。"""


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=2000)


class ProfileItem(BaseModel):
    label: str = Field(max_length=50)
    value: str = Field(max_length=200)


class ProcedureState(BaseModel):
    id: str
    status: Literal["required", "likely", "unrelated"] = Field(description="アプリが端末内で判定した状態")
    reasons: list[str] = Field(default_factory=list, max_length=20, description="当てはまる条件（本人の値を差し込んだもの）")
    completed: bool = False


class ChatRequest(BaseModel):
    procedure_id: str | None = Field(default=None, description="いま見ている手続き。下部タブの「AIに質問」からなら null")
    profile: list[ProfileItem] = Field(default_factory=list, max_length=30, description="回答を表示用の文字にしたもの")
    procedures: list[ProcedureState] = Field(default_factory=list, max_length=50)
    messages: list[ChatMessage] = Field(min_length=1, max_length=30, description="会話（最後はユーザー）")


class ReplyOption(BaseModel):
    label: str = Field(description="選択肢として画面に出す短い文")
    value: str | None = Field(description="field があるとき、その項目に保存する値。無ければ null")


class ChatReply(BaseModel):
    kind: Literal["answer", "question"] = Field(description="answer: 答え / question: ユーザーの状態を聞き返す")
    text: str
    field: str | None = Field(description="聞き返しが「聞き返しに使える項目」に当たるときの項目名。無ければ null")
    options: list[ReplyOption] = Field(description="question のときの選択肢（2〜4個）。answer のときは空")


class ChatUnavailable(Exception):
    """API キーが無い・呼び出し上限・API の失敗で答えられない。message は画面に出す文。"""


_limiter = _RateLimiter(settings.llm_calls_per_minute)


def _askable_fields() -> dict[str, dict[str, str]]:
    """聞き返しの答えをアプリの回答に保存できる項目（1つ選ぶ質問）: field → {value: label}。"""
    return {
        q.field: {c.value: c.label for c in q.choices if c.value}
        for q in build_catalog().questions
        if q.kind == "single"
    }


def _catalog_text() -> str:
    """制度データ（全手続き）。どのユーザーでも同じなので、キャッシュが効くよう system の先頭に置く。"""
    procs = [p.model_dump(exclude_none=True, exclude_defaults=True) for p in load_procedures().values()]
    askable = [
        {"field": q.field, "text": q.text, "choices": [{"value": c.value, "label": c.label} for c in q.choices]}
        for q in build_catalog().questions
        if q.kind == "single"
    ]
    return (
        "# 制度データ（手続き）\n"
        + json.dumps(procs, ensure_ascii=False)
        + "\n\n# 聞き返しに使える項目（選ぶとアプリの回答に保存される）\n"
        + json.dumps(askable, ensure_ascii=False)
    )


def build_context(req: ChatRequest, today: date) -> str:
    """ユーザーごとの情報（ユーザーの情報・手続きの状態・いま見ている手続き）。"""
    procs = load_procedures()
    status_label = {"required": "必要", "likely": "関係がありそう", "unrelated": "対象外"}
    lines = [f"今日の日付: {today.isoformat()}", "", "# ユーザーの情報（アプリで答えた内容。生年月日は送らず年齢だけ）"]
    lines += [f"- {p.label}: {p.value}" for p in req.profile] or ["- （まだ答えていない）"]
    lines += ["", "# アプリが判定した手続きの状態"]
    for s in req.procedures:
        if s.id not in procs:
            continue
        done = "・完了済み" if s.completed else ""
        reasons = f"（{'、'.join(s.reasons)}）" if s.reasons else ""
        lines.append(f"- {procs[s.id].title}［{s.id}］: {status_label[s.status]}{done}{reasons}")
    if req.procedure_id in procs:
        lines += ["", f"# いま見ている手続き: {procs[req.procedure_id].title}［{req.procedure_id}］"]
    return "\n".join(lines)


def sanitize(reply: ChatReply) -> ChatReply:
    """保存できない項目・値の聞き返しは、選択肢だけにする（アプリの回答を壊さないため）。"""
    if reply.kind == "answer":
        return reply.model_copy(update={"field": None, "options": []})
    choices = _askable_fields().get(reply.field or "")
    if choices is None or any(o.value not in choices for o in reply.options):
        return reply.model_copy(
            update={"field": None, "options": [ReplyOption(label=o.label, value=None) for o in reply.options]}
        )
    return reply


async def chat(req: ChatRequest, today: date | None = None) -> ChatReply:
    if not settings.llm_enabled:
        raise ChatUnavailable("AI の設定（API キー）がサーバーにありません")
    if req.messages[-1].role != "user":
        raise ValueError("最後のメッセージはユーザーの質問にしてください")
    if not _limiter.allow():
        raise ChatUnavailable("いま質問が集中しています。少し待ってからもう一度送ってください")

    try:
        response = await _get_client().beta.messages.parse(
            model=settings.claude_model,
            max_tokens=4000,
            output_config={"effort": "low"},
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            system=[
                {"type": "text", "text": SYSTEM_RULES},
                {"type": "text", "text": _catalog_text(), "cache_control": {"type": "ephemeral"}},
                {"type": "text", "text": build_context(req, today or date.today())},
            ],
            messages=[m.model_dump() for m in req.messages],
            output_format=ChatReply,
            timeout=max(settings.llm_timeout_sec, 60),
        )
    except anthropic.APIError as e:
        log.error("Claude API call failed: %s", type(e).__name__)
        raise ChatUnavailable("AI に接続できませんでした。もう一度送ってください") from e

    if response.stop_reason == "refusal" or response.parsed_output is None:
        log.warning("no chat reply (stop_reason=%s)", response.stop_reason)
        raise ChatUnavailable("この質問には答えられませんでした。聞き方を変えてみてください")
    return sanitize(response.parsed_output)
