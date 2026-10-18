from datetime import date

from fastapi.testclient import TestClient

from app.llm.chat import ChatReply, ChatRequest, ReplyOption, build_context, sanitize
from app.main import app

client = TestClient(app)

REQ = {
    "procedure_id": "kokumin_nenkin",
    "profile": [{"label": "年齢", "value": "20歳"}, {"label": "年収", "value": "160万円"}],
    "procedures": [{"id": "kokumin_nenkin", "status": "required", "reasons": ["年齢 20歳（20歳以上）"]}],
    "messages": [{"role": "user", "content": "この手続きは必要？"}],
}


def test_chat_without_api_key_returns_503():
    # テストでは LLM を無効にしている（conftest.py）
    res = client.post("/api/chat", json=REQ)
    assert res.status_code == 503
    assert "API キー" in res.json()["detail"]


def test_chat_last_message_must_be_user():
    body = {**REQ, "messages": [*REQ["messages"], {"role": "assistant", "content": "はい"}]}
    assert client.post("/api/chat", json=body).status_code == 422


def test_context_has_profile_status_and_current_procedure():
    text = build_context(ChatRequest.model_validate(REQ), date(2026, 10, 18))
    assert "年収: 160万円" in text
    assert "必要（年齢 20歳（20歳以上））" in text
    assert "いま見ている手続き" in text and "kokumin_nenkin" in text
    assert "2026-10-18" in text


def test_context_ignores_unknown_procedures():
    req = ChatRequest.model_validate({**REQ, "procedure_id": "nope", "procedures": [{"id": "nope", "status": "likely"}]})
    text = build_context(req, date(2026, 10, 18))
    assert "nope" not in text


def test_sanitize_keeps_known_field_and_values():
    reply = ChatReply(
        kind="question",
        text="親の扶養に入っていますか？",
        field="supported_by",
        options=[ReplyOption(label="入っている", value="parent"), ReplyOption(label="入っていない", value="none")],
    )
    assert sanitize(reply) == reply


def test_sanitize_drops_unknown_field_or_value():
    bad_value = ChatReply(
        kind="question", text="?", field="supported_by",
        options=[ReplyOption(label="祖父母", value="grandparent"), ReplyOption(label="入っていない", value="none")],
    )
    cleaned = sanitize(bad_value)
    assert cleaned.field is None and all(o.value is None for o in cleaned.options)
    assert [o.label for o in cleaned.options] == ["祖父母", "入っていない"]

    unknown_field = ChatReply(kind="question", text="?", field="annual_income", options=[ReplyOption(label="100万円", value="100")])
    assert sanitize(unknown_field).field is None


def test_sanitize_answer_has_no_options():
    reply = ChatReply(kind="answer", text="必要です", field="supported_by", options=[ReplyOption(label="x", value="parent")])
    cleaned = sanitize(reply)
    assert cleaned.field is None and cleaned.options == []
