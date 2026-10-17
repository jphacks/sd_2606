"""質問の定義（data/questions.yaml）を読み、手続きと合わせてアプリに配る形（Catalog）にする。

range の項目は、手続き YAML の条件の境目から選択肢を作る。判定に必要な細かさでしか聞かないため。
たとえば年齢の条件が「20歳以上」「60歳未満」だけなら「19歳以下 / 20〜59歳 / 60歳以上」の3択になる。
"""

import math
from collections.abc import Iterable
from functools import lru_cache
from pathlib import Path
from typing import Literal

import yaml
from pydantic import BaseModel, Field

from app.rules.engine import THRESHOLD, load_procedures
from app.schemas import Catalog, Choice, ProcedureDef, Question, UserProfile

QUESTIONS_FILE = Path(__file__).resolve().parents[2] / "data" / "questions.yaml"


class QuestionDef(BaseModel):
    """questions.yaml の1項目。unit と scale は range の選択肢と number の入力欄の表示に使う。"""

    field: str
    kind: Literal["single", "multi", "range", "number", "date"]
    text: str
    label: str
    initial: bool = False
    none_if_empty: bool = False
    provides: list[str] = Field(default_factory=list, description="この回答からアプリが計算する項目")
    unit: str = ""
    scale: int = Field(default=1, description="表示・入力の単位。年収を万円で扱うなら 10000")
    choices: list[Choice] = Field(default_factory=list)


def cut_points(field: str, procedures: Iterable[ProcedureDef]) -> list[int]:
    """field の値が境目 c 以上か未満かで、どれかの条件の結果が変わるような c の一覧。"""
    cuts: set[int] = set()
    for proc in procedures:
        for cond in [c for rel in proc.relations for c in (rel, *rel.when)]:
            if cond.field == field:
                for v in cond.value if isinstance(cond.value, list) else [cond.value]:
                    v = int(v)
                    if cond.op in ("gte", "lt"):
                        cuts.add(v)
                    elif cond.op in ("gt", "lte"):
                        cuts.add(v + 1)
                    else:  # eq / ne / in / not_in はその値だけを切り出す
                        cuts.update({v, v + 1})
    return sorted(c for c in cuts if c > 0)


def _num(n: int, q: QuestionDef) -> str:
    v = n / q.scale
    return f"{v:,.0f}" if v == int(v) else f"{v:,}"


def _from(lo: int, q: QuestionDef) -> str:
    """「lo 以上」。年収の「20,000,001円以上」は「2,000万円超」と書く。"""
    if q.scale > 1 and lo % q.scale == 1:
        return f"{_num(lo - 1, q)}{q.unit}超"
    return f"{_num(lo, q)}{q.unit}以上"


def _upto(hi: int, q: QuestionDef) -> str:
    """「hi 以下」。年収の「1,499,999円以下」は「150万円未満」と書く。"""
    if q.scale > 1 and (hi + 1) % q.scale == 0:
        return f"{_num(hi + 1, q)}{q.unit}未満"
    return f"{_num(hi, q)}{q.unit}以下"


def range_label(lo: int, hi: int | None, q: QuestionDef) -> str:
    if hi is None:
        return _from(lo, q)
    if lo == hi:
        return f"{_num(lo, q)}{q.unit}"
    if lo == 0:
        return _upto(hi, q)
    if q.scale > 1 and lo % q.scale == 1:
        return f"{_from(lo, q)}〜{_num(hi, q)}{q.unit}以下"
    if q.scale > 1 and (hi + 1) % q.scale == 0:
        return f"{_from(lo, q)}{_upto(hi, q)}"
    return f"{_num(lo, q)}〜{_num(hi, q)}{q.unit}"


def range_choices(q: QuestionDef, procedures: Iterable[ProcedureDef]) -> list[Choice]:
    bounds = [0, *cut_points(q.field, procedures)]
    choices = []
    for i, lo in enumerate(bounds):
        hi = bounds[i + 1] - 1 if i + 1 < len(bounds) else None
        choices.append(Choice(label=range_label(lo, hi, q), min=lo, max=hi))
    return choices


@lru_cache
def load_question_defs(path: Path = QUESTIONS_FILE) -> list[QuestionDef]:
    defs = [QuestionDef.model_validate(d) for d in yaml.safe_load(path.read_text(encoding="utf-8"))]
    for q in defs:
        for f in q.provides or [q.field]:
            if f not in UserProfile.model_fields:
                raise ValueError(f"{path.name}: unknown field {f}")
        if q.kind in ("single", "multi") and not q.choices:
            raise ValueError(f"{path.name}: {q.field} に choices がありません")
    return defs


@lru_cache
def build_catalog() -> Catalog:
    procedures = list(load_procedures().values())
    questions = []
    for q in load_question_defs():
        choices = range_choices(q, procedures) if q.kind == "range" else q.choices
        if q.kind == "range" and len(choices) < 2:  # どの条件でも使われていない range の項目は聞かない
            continue
        questions.append(
            Question(
                field=q.field,
                kind=q.kind,
                text=q.text,
                label=q.label,
                initial=q.initial,
                none_if_empty=q.none_if_empty,
                choices=choices,
                unit=q.unit,
                scale=q.scale,
                provides=q.provides,
            )
        )
    # JSON は -inf を表せないので、下限なしは null で配る
    return Catalog(procedures=procedures, questions=questions, threshold=None if math.isinf(THRESHOLD) else THRESHOLD)
