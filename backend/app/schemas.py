"""API の入出力スキーマ。Android 側の data/Models.kt と対応させること。"""

from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator

Occupation = Literal[
    "employee",  # 会社員・公務員
    "part_time",  # パート・アルバイト
    "self_employed",  # 自営業
    "freelance",  # フリーランス
    "student",  # 学生
    "unemployed",  # 無職・求職中
    "retired",  # 退職済み（年金生活など）
]

HealthInsurance = Literal[
    "employer",  # 勤務先の健康保険（協会けんぽ・健保組合・共済）
    "national",  # 国民健康保険
    "dependent",  # 家族の扶養
    "unknown",
]

LifeEvent = Literal[
    "new_job",  # 就職・転職
    "resign",  # 退職
    "marriage",  # 結婚
    "childbirth",  # 出産
    "move",  # 引越し
    "start_business",  # 開業・独立
    "side_job",  # 副業を始めた
    "turn_20",  # 20歳になった
    "high_medical_expense",  # 医療費が多くかかった
]

Category = Literal["tax", "insurance", "pension", "benefit", "registration"]


class UserProfile(BaseModel):
    age: int = Field(ge=0, le=120)
    age_at_year_end: int | None = Field(
        default=None, ge=0, le=120, description="その年の12月31日時点の年齢（所得税の控除などはこの年齢で判定する）"
    )
    occupation: list[Occupation] = Field(description="働き方（複数可。例: 会社員とフリーランスの副業）")
    annual_income: int = Field(ge=0, description="年収（円）")
    health_insurance: HealthInsurance = "unknown"
    has_spouse: bool = False
    dependents: int = Field(default=0, ge=0, description="扶養している家族の人数")
    life_events: list[LifeEvent] = Field(default_factory=list)

    @field_validator("occupation", mode="before")
    @classmethod
    def _occupation_as_list(cls, v: object) -> object:
        return [v] if isinstance(v, str) else v  # 1つだけなら文字列でも受け付ける


class Link(BaseModel):
    label: str
    url: str


class FlowStep(BaseModel):
    title: str
    detail: str
    where: str | None = None
    documents: list[str] = Field(default_factory=list)


class Procedure(BaseModel):
    """data/procedures/*.yaml の1ファイル分（判定条件 match は除く）。"""

    id: str
    title: str
    category: Category
    summary: str
    priority: int = Field(ge=0, le=100, description="大きいほど優先して表示")
    deadline: str | None = None
    office: str | None = None
    steps: list[FlowStep] = Field(default_factory=list)
    links: list[Link] = Field(default_factory=list)


class Recommendation(BaseModel):
    procedure_id: str
    title: str
    category: Category
    summary: str
    priority: int
    deadline: str | None = None
    reasons: list[str] = Field(description="この人に該当すると判定した理由（ルールから）")
    personal_note: str | None = Field(default=None, description="LLM による個別の補足。生成できなければ null")


class RecommendationResponse(BaseModel):
    items: list[Recommendation]
    overall_advice: str | None = Field(default=None, description="LLM による全体のアドバイス")
    ai_generated: bool = False


# --- 端末内で判定するための配信データ（GET /api/catalog）---


class Condition(BaseModel):
    field: str
    op: str
    value: Any


class MatchClause(BaseModel):
    """when の条件をすべて満たせば、reason を理由としてこの手続きが該当する。"""

    reason: str
    when: list[Condition]


class ProcedureDef(Procedure):
    """判定条件 match を含む手続き。アプリはこれを受け取り、端末内で判定する。"""

    match: list[MatchClause]


class Choice(BaseModel):
    label: str
    value: str | None = Field(default=None, description="single / multi の選択肢の値")
    min: int | None = Field(default=None, description="range の下限（この値を含む）")
    max: int | None = Field(default=None, description="range の上限（この値を含む）。null は上限なし")


class Question(BaseModel):
    field: str = Field(description="回答の項目名。UserProfile の項目名か、birth_date")
    kind: Literal["single", "multi", "range", "date"]
    text: str
    initial: bool = Field(default=False, description="true なら最初の画面で聞く。false は判定に必要になったときだけ聞く")
    none_if_empty: bool = Field(
        default=False, description="multi で何も選ばなかったとき、未回答ではなく「どれにも当てはまらない」として判定する"
    )
    choices: list[Choice] = Field(default_factory=list)
    provides: list[str] = Field(
        default_factory=list,
        description="この回答から計算する項目（birth_date → age, age_at_year_end）。計算はアプリが端末内で行う",
    )


class Catalog(BaseModel):
    procedures: list[ProcedureDef]
    questions: list[Question]
