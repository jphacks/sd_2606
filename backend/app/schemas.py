"""API の入出力スキーマ。Android 側の data/model/Models.kt と対応させること。"""

from typing import Literal

from pydantic import BaseModel, Field

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
    occupation: Occupation
    annual_income: int = Field(ge=0, description="年収（円）")
    health_insurance: HealthInsurance = "unknown"
    has_spouse: bool = False
    dependents: int = Field(default=0, ge=0, description="扶養している家族の人数")
    life_events: list[LifeEvent] = Field(default_factory=list)


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
