"""体験談の掲示板の API。アカウントは無く、投稿は匿名（年代・立場は本人が選んだときだけ付く）。"""

import re
from datetime import date

from fastapi import APIRouter, Header, HTTPException, Query, Request, Response

from app.board import db
from app.board.definitions import load_meta, tags_for_procedure
from app.board.moderation import RateLimiter, find_personal_info
from app.board.schemas import BoardMeta, CreatedPost, Helpful, NewPost, Post, PostPage, Summary
from app.config import settings

router = APIRouter(prefix="/api/board", tags=["board"])

post_limiter = RateLimiter(settings.board_posts_per_10min, 600)
vote_limiter = RateLimiter(60, 60)

CLIENT_ID = re.compile(r"^[A-Za-z0-9-]{8,64}$")


def _client(request: Request) -> str:
    # uvicorn は --proxy-headers で、Funnel / トンネルが付ける X-Forwarded-For の送信元を client にする
    return request.client.host if request.client else "unknown"


def _client_id(value: str | None) -> str:
    if not value or not CLIENT_ID.match(value):
        raise HTTPException(400, "X-Client-Id が必要です")
    return value


def _tag_filter(tag: str | None, procedure: str | None) -> list[str] | None:
    if procedure:
        tags = tags_for_procedure(procedure)
        return [t for t in tags if t == tag] if tag else tags
    return [tag] if tag else None


@router.get("/meta", response_model=BoardMeta)
def get_meta() -> BoardMeta:
    """タグ（手続きとの対応つき）と、投稿の種類・年代・立場・かかった時間の選択肢。"""
    return load_meta()


@router.get("/posts", response_model=PostPage)
def list_posts(
    tag: str | None = None,
    procedure: str | None = Query(None, description="この手続きに対応するタグの投稿"),
    kind: str | None = None,
    sort: str = Query("new", pattern="^(new|helpful)$"),
    limit: int = Query(20, ge=1, le=50),
    offset: int = Query(0, ge=0),
) -> PostPage:
    items, more = db.list_posts(_tag_filter(tag, procedure), kind, sort, limit, offset)
    return PostPage(items=items, next_offset=offset + limit if more else None)


@router.get("/summary", response_model=Summary)
def get_summary(procedure: str) -> Summary:
    """手続きの画面の見出し（件数・大変さの平均・かかった時間の中央値）。"""
    return db.summary(tags_for_procedure(procedure))


@router.get("/counts", response_model=dict[str, int])
def get_counts() -> dict[str, int]:
    """手続きごとの体験談の件数（おすすめ一覧のカード用）。"""
    by_tag = db.count_by_tag()
    procedures = {p for t in load_meta().tags for p in t.procedures}
    return {p: len(set().union(*(by_tag.get(t, set()) for t in tags_for_procedure(p)))) for p in sorted(procedures)}


@router.get("/posts/{post_id}", response_model=Post)
def get_post(post_id: int) -> Post:
    post = db.get(post_id)
    if post is None:
        raise HTTPException(404, "投稿が見つかりません（削除されたか、非表示になっています）")
    return post


def _validate(post: NewPost) -> None:
    meta = load_meta()

    def check(value, allowed, name):
        if value is not None and value not in allowed:
            raise HTTPException(422, f"{name}の値が正しくありません")

    check(post.kind, {o.value for o in meta.kinds}, "種類")
    check(post.age_band, {o.value for o in meta.age_bands}, "年代")
    check(post.occupation, {o.value for o in meta.occupations}, "立場")
    check(post.minutes, {d.value for d in meta.durations}, "かかった時間")
    tag_ids = {t.id for t in meta.tags}
    for tag in post.tags:
        check(tag, tag_ids, "タグ")
    if post.happened_ym and post.happened_ym > date.today().strftime("%Y-%m"):
        raise HTTPException(422, "「いつの話か」に未来の月は選べません")
    found = find_personal_info(f"{post.title}\n{post.body}")
    if found:
        raise HTTPException(422, f"{found}のようなものが含まれています。個人が特定される情報は書かないでください")


@router.post("/posts", response_model=CreatedPost, status_code=201)
def create_post(post: NewPost, request: Request) -> CreatedPost:
    _validate(post)
    if not post_limiter.allow(_client(request)):
        raise HTTPException(429, "短い時間に投稿が続いています。しばらくしてからもう一度お試しください")
    created, token = db.create(post)
    return CreatedPost(post=created, delete_token=token)


@router.delete("/posts/{post_id}", status_code=204)
def delete_post(post_id: int, x_delete_token: str = Header()) -> Response:
    if not db.delete(post_id, x_delete_token):
        raise HTTPException(404, "投稿が見つからないか、削除できません")
    return Response(status_code=204)


def _vote(request: Request) -> None:
    if not vote_limiter.allow(_client(request)):
        raise HTTPException(429, "操作が多すぎます。しばらくしてからもう一度お試しください")


@router.put("/posts/{post_id}/helpful", response_model=Helpful)
def add_helpful(post_id: int, request: Request, x_client_id: str | None = Header(None)) -> Helpful:
    return _helpful(post_id, request, x_client_id, True)


@router.delete("/posts/{post_id}/helpful", response_model=Helpful)
def remove_helpful(post_id: int, request: Request, x_client_id: str | None = Header(None)) -> Helpful:
    return _helpful(post_id, request, x_client_id, False)


def _helpful(post_id: int, request: Request, x_client_id: str | None, on: bool) -> Helpful:
    client_id = _client_id(x_client_id)
    _vote(request)
    count = db.set_helpful(post_id, client_id, on)
    if count is None:
        raise HTTPException(404, "投稿が見つかりません")
    return Helpful(helpful=count)


@router.post("/posts/{post_id}/report", status_code=204)
def report_post(post_id: int, request: Request, x_client_id: str | None = Header(None)) -> Response:
    """通報。決まった件数（既定 3）に達すると非表示になる。"""
    client_id = _client_id(x_client_id)
    _vote(request)
    if not db.report(post_id, client_id):
        raise HTTPException(404, "投稿が見つかりません")
    return Response(status_code=204)
