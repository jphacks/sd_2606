import pytest
from fastapi.testclient import TestClient

from app.board.definitions import load_meta, load_samples
from app.board.moderation import find_personal_info
from app.main import app
from app.routers import board
from app.rules.engine import load_procedures

client = TestClient(app)
CID = {"X-Client-Id": "test-client-0001"}


@pytest.fixture(autouse=True)
def _reset_limits():
    board.post_limiter.reset()
    board.vote_limiter.reset()


def new_post(**kw) -> dict:
    return {
        "kind": "howto",
        "title": "テスト投稿のタイトル",
        "body": "窓口で手続きをしました。必要な書類は二つだけでした。",
        "tags": ["kokumin_nenkin"],
        "age_band": "20s_early",
        "occupation": "student",
        "happened_ym": "2026-04",
        "difficulty": 2,
        "minutes": 30,
    } | kw


def test_tag_procedures_exist():
    procedures = set(load_procedures())
    for tag in load_meta().tags:
        assert set(tag.procedures) <= procedures, tag.id


def test_samples_are_valid_and_loaded():
    meta = load_meta()
    tag_ids = {t.id for t in meta.tags}
    for s in load_samples():
        assert set(s["tags"]) <= tag_ids, s["key"]
        assert find_personal_info(s["title"] + s["body"]) is None, s["key"]
    items = client.get("/api/board/posts", params={"limit": 50}).json()["items"]
    assert sum(p["sample"] for p in items) >= len(load_samples())


def test_occupations_match_profile():
    # 掲示板の立場は、最初の画面で聞かない会社員・自営業も含む。値は回答と同じ集合から選ぶ
    from typing import get_args

    from app.schemas import Occupation

    assert {o.value for o in load_meta().occupations} <= set(get_args(Occupation))


def test_create_list_and_delete():
    res = client.post("/api/board/posts", json=new_post(title="作って消す投稿"))
    assert res.status_code == 201
    created = res.json()
    post_id = created["post"]["id"]
    assert created["post"]["sample"] is False

    by_procedure = client.get("/api/board/posts", params={"procedure": "gakusei_tokurei"}).json()["items"]
    assert by_procedure[0]["id"] == post_id  # 新しい順。国民年金のタグは学生納付特例の画面にも出る
    other = client.get("/api/board/posts", params={"procedure": "hikkoshi", "limit": 50}).json()["items"]
    assert post_id not in [p["id"] for p in other]

    assert client.delete(f"/api/board/posts/{post_id}", headers={"X-Delete-Token": "wrong"}).status_code == 404
    assert client.delete(f"/api/board/posts/{post_id}", headers={"X-Delete-Token": created["delete_token"]}).status_code == 204
    assert client.get(f"/api/board/posts/{post_id}").status_code == 404


@pytest.mark.parametrize(
    "text",
    ["電話は 090-1234-5678 まで", "mail: someone@example.com", "番号は1234 5678 9012", "〒980-8579 仙台市", "http://example.com/ad"],
)
def test_rejects_personal_info(text):
    res = client.post("/api/board/posts", json=new_post(body=f"手続きのことを書きます。{text}"))
    assert res.status_code == 422
    assert "個人が特定" in res.json()["detail"]


def test_allows_amounts_dates_and_official_links():
    body = "年収 1,030,000円 と 2026-03-15 に確定申告。https://www.nta.go.jp/taxes/ を見ました。"
    assert find_personal_info(body) is None


@pytest.mark.parametrize("bad", [{"tags": ["nope"]}, {"kind": "ad"}, {"minutes": 7}, {"happened_ym": "2999-01"}, {"tags": []}])
def test_rejects_invalid_values(bad):
    assert client.post("/api/board/posts", json=new_post(**bad)).status_code == 422


def test_rate_limit():
    for _ in range(board.post_limiter.limit):
        assert client.post("/api/board/posts", json=new_post()).status_code == 201
    assert client.post("/api/board/posts", json=new_post()).status_code == 429


def test_helpful_once_per_client_and_undo():
    post_id = client.post("/api/board/posts", json=new_post()).json()["post"]["id"]
    url = f"/api/board/posts/{post_id}/helpful"
    assert client.put(url, headers=CID).json() == {"helpful": 1}
    assert client.put(url, headers=CID).json() == {"helpful": 1}
    assert client.put(url, headers={"X-Client-Id": "test-client-0002"}).json() == {"helpful": 2}
    assert client.delete(url, headers=CID).json() == {"helpful": 1}
    assert client.put(url).status_code == 400  # client id が無い


def test_reports_hide_post():
    post_id = client.post("/api/board/posts", json=new_post()).json()["post"]["id"]
    for i in range(3):
        assert client.post(f"/api/board/posts/{post_id}/report", headers={"X-Client-Id": f"reporter-{i:04d}"}).status_code == 204
    assert client.get(f"/api/board/posts/{post_id}").status_code == 404


def test_summary_and_counts():
    s = client.get("/api/board/summary", params={"procedure": "kokuho_enroll"}).json()
    assert s["count"] >= 1 and s["avg_difficulty"] is not None and s["median_minutes"] in {15, 30, 60, 180, 480}
    counts = client.get("/api/board/counts").json()
    assert counts["kokuho_enroll"] == s["count"]
