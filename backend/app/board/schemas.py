"""掲示板の API の入出力。Android 側の data/BoardModels.kt と対応させること。"""

from pydantic import BaseModel, Field


class Option(BaseModel):
    """選択肢。age_bands だけ min / max（年齢、max は含む。null は上限なし）を持つ。"""

    value: str
    label: str
    min: int | None = None
    max: int | None = None


class Duration(BaseModel):
    value: int = Field(description="分")
    label: str


class Tag(BaseModel):
    id: str
    label: str
    group: str
    procedures: list[str] = Field(default_factory=list, description="この手続きの画面に、このタグの体験談を出す")


class BoardMeta(BaseModel):
    kinds: list[Option]
    age_bands: list[Option]
    occupations: list[Option]
    durations: list[Duration]
    tag_groups: list[Option]
    tags: list[Tag]


class NewPost(BaseModel):
    kind: str
    title: str = Field(min_length=4, max_length=60)
    body: str = Field(min_length=20, max_length=2000)
    tags: list[str] = Field(min_length=1, max_length=3)
    age_band: str | None = None
    occupation: str | None = None
    happened_ym: str | None = Field(default=None, pattern=r"^\d{4}-(0[1-9]|1[0-2])$", description="いつの話か（2026-03）")
    difficulty: int | None = Field(default=None, ge=1, le=5, description="大変さ（1〜5）")
    minutes: int | None = Field(default=None, description="かかった時間（durations の value）")


class Post(BaseModel):
    id: int
    kind: str
    title: str
    body: str
    tags: list[str]
    age_band: str | None = None
    occupation: str | None = None
    happened_ym: str | None = None
    difficulty: int | None = None
    minutes: int | None = None
    helpful: int = 0
    sample: bool = Field(default=False, description="開発チームが書いたサンプル")
    created_at: str


class PostPage(BaseModel):
    items: list[Post]
    next_offset: int | None = Field(default=None, description="続きがあれば次の offset")


class CreatedPost(BaseModel):
    post: Post
    delete_token: str = Field(description="自分の投稿を消すためのトークン。端末にだけ保存する（サーバーはハッシュだけを持つ）")


class Summary(BaseModel):
    """手続きの画面の「みんなの体験談」の見出し用。"""

    count: int
    avg_difficulty: float | None = None
    median_minutes: int | None = None
    kinds: dict[str, int] = Field(default_factory=dict)


class Helpful(BaseModel):
    helpful: int
