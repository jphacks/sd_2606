"""API の入出力スキーマ。Android 側の data/Models.kt と対応させること。"""

from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator, model_validator

Occupation = Literal[
    "employee",  # 会社員・公務員
    "part_time",  # パート・アルバイト
    "self_employed",  # 自営業
    "freelance",  # フリーランス
    "student",  # 学生
    "unemployed",  # 無職・求職中
    "retired",  # 退職済み（年金生活など）
]

SupportedBy = Literal[
    "parent",  # 親の扶養に入っている
    "spouse",  # 配偶者の扶養に入っている
    "none",  # 入っていない
    "unknown",
]

HealthInsurance = Literal[
    "employer",  # 勤務先の健康保険（協会けんぽ・健保組合・共済）
    "national",  # 国民健康保険
    "dependent",  # 家族の扶養
    "unknown",
]

YesNo = Literal["yes", "no"]

Category = Literal["tax", "insurance", "pension", "benefit", "registration"]

# きっかけの回答（年齢・働き方など）と手続きの関係: required（必須）/ conditional（条件次第）/ unrelated（無関係）
Flag = Literal["required", "conditional", "unrelated"]


class UserProfile(BaseModel):
    age: int = Field(ge=0, le=120)
    age_at_year_end: int | None = Field(
        default=None, ge=0, le=120, description="その年の12月31日時点の年齢（所得税の控除などはこの年齢で判定する）"
    )
    occupation: list[Occupation] = Field(default_factory=lambda: ["student"], description="働き方（複数可）。アプリの対象は学生なので省くと学生")
    part_time: Literal["yes", "no"] | None = Field(default=None, description="アルバイトをしているか（yes / no）")
    annual_income: int = Field(ge=0, description="年収（円）")
    health_insurance: HealthInsurance = "unknown"
    supported_by: SupportedBy | None = Field(default=None, description="誰の扶養に入っているか（税・健康保険）")
    prev_health_insurance: HealthInsurance | None = Field(default=None, description="就職する前に入っていた健康保険")
    has_spouse: bool = False
    dependents: int = Field(default=0, ge=0, description="扶養している家族の人数")
    multiple_jobs: YesNo | None = Field(default=None, description="今年、給与をもらった勤務先が2か所以上あるか（掛け持ち）")

    @field_validator("occupation", mode="before")
    @classmethod
    def _occupation_as_list(cls, v: object) -> object:
        return [v] if isinstance(v, str) else v  # 1つだけなら文字列でも受け付ける


class Link(BaseModel):
    label: str
    url: str


class Condition(BaseModel):
    field: str
    op: str = "eq"
    value: Any


class FlowStep(BaseModel):
    """手続きの1ステップ。title / detail の {annual_income} などは、アプリがその人の回答（140万円・20歳など）に置き換える。"""

    title: str = ""
    detail: str = ""
    where: str | None = None
    documents: list[str] = Field(default_factory=list, description="このステップで必要なもの。アプリは全ステップ分をまとめて一覧にする")
    details: list[str] = Field(
        default_factory=list, description="詳しい手順（箇条書き）。アプリでは最初は閉じておき、開くと表示する"
    )
    when: list[Condition] = Field(
        default_factory=list,
        description="このステップの説明が当てはまる人の条件。満たさない条件があればアプリは表示しない（未回答なら表示する）",
    )
    choice: "FlowChoice | None" = Field(
        default=None, description="フローの分岐。書いたときは title / detail は書かない。選ぶまで、この先のステップと金額は出さない"
    )

    @model_validator(mode="after")
    def _step_or_choice(self) -> "FlowStep":
        if (self.choice is None) == (not self.title):
            raise ValueError("ステップには title（と detail）か choice のどちらか一方を書いてください")
        return self


class Due(BaseModel):
    """期限順に並べるための期限。days（きっかけから何日以内）か、month / day（毎年の期限）のどちらかを書く。"""

    days: int | None = Field(default=None, ge=1, description="きっかけから何日以内（並べ替えでは今日をきっかけの日とみなす）")
    month: int | None = Field(default=None, ge=1, le=12, description="毎年の期限の月")
    day: int | None = Field(default=None, ge=1, le=31, description="毎年の期限の日。省くと月末")

    @model_validator(mode="after")
    def _one_kind(self) -> "Due":
        if (self.days is None) == (self.month is None) or (self.day is not None and self.month is None):
            raise ValueError("due には days か month（と day）のどちらか一方を書いてください")
        return self


class Reminder(BaseModel):
    """毎年・毎月の期限（例: 確定申告 3/15、国民年金の保険料 毎月末）。アプリの「通知と履歴」に、完了した手続きと一緒に時系列で並べる。"""

    label: str = Field(description="通知に出す期限の名前（例: 確定申告の期限）")
    month: int | None = Field(default=None, ge=1, le=12, description="期限の月。省くと毎月")
    day: int = Field(ge=1, le=31, description="期限の日。その月に無い日（31日など）は月末")
    before_days: int = Field(default=7, ge=0, le=60, description="期限の何日前から「もうすぐ」と目立たせるか")
    when: list[Condition] = Field(default_factory=list, description="この期限を出す人の条件（ステップの when と同じ意味）")


AmountKind = Literal["juminzei", "shotokuzei", "parent_deduction", "nenkin", "nenkin_tokurei"]


class Amount(BaseModel):
    """手続きの画面に出すお金の目安。金額はアプリが回答（年収など）から端末内で計算する（サーバーは計算しない）。"""

    kind: AmountKind = Field(
        description="juminzei: 本人の住民税 / shotokuzei: 本人の所得税 / parent_deduction: 親が受ける控除"
        " / nenkin: 国民年金保険料 / nenkin_tokurei: 学生納付特例を使うときの保険料"
    )
    card: bool = Field(default=False, description="一覧のカードにも1行で出す（1つの手続きで1つまで）")
    when: list[Condition] = Field(default_factory=list, description="この金額を出す人の条件（ステップの when と同じ意味）")


class FlowOption(BaseModel):
    """フローの分岐の選択肢1つ。選ぶと steps と amounts を出す。"""

    value: str = Field(description="選んだときに回答として保存する値（choice の field に入る）")
    label: str = Field(description="選択肢の名前（例: 学生納付特例を使う）")
    detail: str = Field(description="選択肢の簡潔な説明（1文）")
    steps: list[FlowStep] = Field(default_factory=list)
    amounts: list[Amount] = Field(default_factory=list, description="この選択肢を選んだときに出すお金の目安")


class FlowChoice(BaseModel):
    """フローの分岐（例: 国民年金の保険料を、学生納付特例で待ってもらうか納めるか）。"""

    field: str = Field(description="選んだ値を保存する回答の項目名（手続きの中で一意。reminders の when にも使える）")
    title: str = Field(description="分岐の見出し（例: 保険料をどうするか選ぶ）")
    options: list[FlowOption] = Field(min_length=2)


FlowStep.model_rebuild()


class Procedure(BaseModel):
    """data/procedures/*.yaml の1ファイル分（判定条件 relations は除く）。"""

    id: str
    title: str
    category: Category
    summary: str
    deadline: str | None = None
    due: Due | None = Field(default=None, description="期限順の並べ替えに使う期限。期限が無いものは最後に並ぶ")
    office: str | None = None
    steps: list[FlowStep] = Field(default_factory=list)
    links: list[Link] = Field(default_factory=list)
    reminders: list[Reminder] = Field(default_factory=list, description="毎年・毎月の期限（通知と履歴の画面に並べる）")
    amounts: list[Amount] = Field(default_factory=list, description="手続きの画面に出すお金の目安")


class Recommendation(BaseModel):
    procedure_id: str
    title: str
    category: Category
    summary: str
    required: bool = Field(description="必須と判定した（false なら「関係がありそう」）")
    score: float = Field(description="関係の強さ（数えた relations の点数の合計）")
    deadline: str | None = None
    reasons: list[str] = Field(description="この人に該当すると判定した理由（ルールから）")
    personal_note: str | None = Field(default=None, description="LLM による個別の補足。生成できなければ null")


class RecommendationResponse(BaseModel):
    items: list[Recommendation]
    overall_advice: str | None = Field(default=None, description="LLM による全体のアドバイス")
    ai_generated: bool = False


# --- 端末内で判定するための配信データ（GET /api/catalog）---


class Relation(Condition):
    """きっかけ（field / op / value。例: 働き方が学生）に当てはまるときの、手続きとの関係。"""

    flag: Flag = Field(
        description="required: 必須 / conditional: when をすべて満たせば必須、無ければ点数だけ（条件次第）"
        " / unrelated: 対象外（when があれば、すべて満たしたときだけ）"
    )
    score: float = Field(ge=-1, le=1, description="関係の強さ。1: 強く関係する ～ -1: 関係が薄い")
    reason: str | None = Field(default=None, description="画面に出す理由（必要・関係がありそう・対象外の理由）")
    when: list[Condition] = Field(
        default_factory=list,
        description="conditional / unrelated の条件。満たさない条件があればこの行は数えない。未回答があればアプリが聞く",
    )


class ProcedureDef(Procedure):
    """判定条件を含む手続き。アプリはこれを受け取り、端末内で判定する。"""

    relations: list[Relation] = Field(description="きっかけの回答ごとの関係（状態・点数・理由）")


class Choice(BaseModel):
    label: str
    value: str | None = Field(default=None, description="single / multi の選択肢の値")
    min: int | None = Field(default=None, description="range の下限（この値を含む）")
    max: int | None = Field(default=None, description="range の上限（この値を含む）。null は上限なし")


class Question(BaseModel):
    field: str = Field(description="回答の項目名。UserProfile の項目名か、birth_date")
    kind: Literal["single", "multi", "range", "number", "date"]
    text: str
    label: str = Field(description="項目の短い名前。「判定には生年月日が必要です」のような案内に使う")
    required: bool = Field(
        default=False, description="true なら最初の画面で必ず答えてもらう。false は任意（最初の画面の「もっと詳しく」と、判定に必要になった手続きで聞く）"
    )
    none_if_empty: bool = Field(
        default=False, description="multi で何も選ばなかったとき、未回答ではなく「どれにも当てはまらない」として判定する"
    )
    inline: bool = Field(
        default=False, description="true なら最初の画面では聞かず、判定に必要になった手続きのカードの中でだけ質問文をそのまま出して聞く"
    )
    choices: list[Choice] = Field(default_factory=list)
    unit: str = Field(default="", description="number の単位（画面の表示用）")
    scale: int = Field(default=1, description="number の入力の単位。年収を万円で入れるなら 10000（保存は円）")
    provides: list[str] = Field(
        default_factory=list,
        description="この回答から計算する項目（birth_date → age, age_at_year_end）。計算はアプリが端末内で行う",
    )


class Catalog(BaseModel):
    procedures: list[ProcedureDef]
    questions: list[Question]
    threshold: float | None = Field(
        description="必須でも対象外でもない手続きは、点数の合計がこの値以上なら「関係がありそうな手続き」に出す。null は下限なし（-inf）"
    )
