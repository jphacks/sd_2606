"""制度データ（YAML）とユーザープロフィールを突き合わせ、該当する手続きを返すルールエンジン。

YAML の書き方は docs/procedures.md を参照。
"""

import re
from collections.abc import Callable, Iterator
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml

from app.schemas import Condition, FlowStep, ProcedureDef, Recommendation, Relation, UserProfile

DATA_DIR = Path(__file__).resolve().parents[2] / "data" / "procedures"
QUESTIONS_FILE = DATA_DIR.parent / "questions.yaml"
_PLACEHOLDER = re.compile(r"\{(\w+)\}")

# 必須でも対象外でもない手続きは、点数の合計がこの値以上なら「関係がありそうな手続き」に出す（アプリには Catalog で配る）。
# いまは下限なし（-inf）で、対象外（unrelated）と判定したもの以外はすべて出す。点数は並び順に使う
THRESHOLD = float("-inf")

OPS: dict[str, Callable[[Any, Any], bool]] = {
    "eq": lambda a, b: a == b,
    "ne": lambda a, b: a != b,
    "in": lambda a, b: a in b,
    "not_in": lambda a, b: a not in b,
    "contains": lambda a, b: b in a,
    "gte": lambda a, b: a >= b,
    "gt": lambda a, b: a > b,
    "lte": lambda a, b: a <= b,
    "lt": lambda a, b: a < b,
}


# 複数選択の項目（occupation）では、eq / in は「選んだどれかが当てはまる」、
# ne / not_in は「選んだどれも当てはまらない」。アプリの RuleEngine.kt も同じ意味で判定する
LIST_OPS: dict[str, Callable[[list, Any], bool]] = {
    "eq": lambda a, b: b in a,
    "ne": lambda a, b: b not in a,
    "in": lambda a, b: any(x in b for x in a),
    "not_in": lambda a, b: not any(x in b for x in a),
}


def _check(cond: Condition, profile: UserProfile) -> bool:
    if cond.op not in OPS:
        raise ValueError(f"unknown op: {cond.op}")
    value = getattr(profile, cond.field)
    if value is None:  # 未入力の項目を使う条件は満たさないものとする
        return False
    if isinstance(value, list) and cond.op in LIST_OPS:
        return LIST_OPS[cond.op](value, cond.value)
    return OPS[cond.op](value, cond.value)


@lru_cache
def load_procedures(data_dir: Path = DATA_DIR) -> dict[str, ProcedureDef]:
    procs: dict[str, ProcedureDef] = {}
    for path in sorted(data_dir.glob("*.yaml")):
        proc = ProcedureDef.model_validate(yaml.safe_load(path.read_text(encoding="utf-8")))
        if proc.id != path.stem:
            raise ValueError(f"{path.name}: id ({proc.id}) とファイル名を一致させてください")
        if not proc.relations:
            raise ValueError(f"{path.name}: relations がありません")
        steps = list(all_steps(proc.steps))
        choices = {s.choice.field for s in steps if s.choice}  # フローの分岐で選んだ値も、回答と同じように条件に使える
        amounts = [*proc.amounts, *(a for s in steps if s.choice for o in s.choice.options for a in o.amounts)]
        conds = [c for rel in proc.relations for c in (rel, *rel.when)]
        conds += [c for step in steps for c in step.when] + [c for r in proc.reminders for c in r.when]
        conds += [c for a in amounts for c in a.when]
        for cond in conds:
            if cond.field not in UserProfile.model_fields and cond.field not in choices:
                raise ValueError(f"{path.name}: unknown field {cond.field}")
            if cond.op not in OPS:
                raise ValueError(f"{path.name}: unknown op {cond.op}")
        for rel in proc.relations:
            _validate_relation(path.name, rel)
        procs[proc.id] = proc
    return procs


def all_steps(steps: list[FlowStep]) -> Iterator[FlowStep]:
    """ステップを、分岐の選択肢の中のものも含めて順に返す。"""
    for step in steps:
        yield step
        if step.choice:
            for option in step.choice.options:
                yield from all_steps(option.steps)


def _validate_relation(name: str, rel: Relation) -> None:
    """flag と点数・理由・when の組み合わせを確かめる（書き方は docs/procedures.md）。"""
    if rel.flag == "required" and (rel.score <= 0 or not rel.reason or rel.when):
        raise ValueError(f"{name}: required（必須）には正の点数と reason を書き、when は書かないでください")
    if rel.flag == "unrelated" and rel.score > 0:
        raise ValueError(f"{name}: unrelated（対象外）の点数は 0 以下にしてください")
    if rel.flag == "conditional" and rel.score > 0 and not rel.reason:
        raise ValueError(f"{name}: 正の点数の conditional には reason を書いてください")
    if rel.flag == "conditional" and rel.when and rel.score <= 0:
        raise ValueError(f"{name}: when のある conditional（満たせば必須）の点数は正にしてください")


@lru_cache
def _choice_labels() -> dict[str, dict[str, str]]:
    defs = yaml.safe_load(QUESTIONS_FILE.read_text(encoding="utf-8"))
    return {d["field"]: {c["value"]: c["label"] for c in d.get("choices") or [] if "value" in c} for d in defs}


def fill_in(text: str, profile: UserProfile) -> str:
    """理由の {annual_income} などをプロフィールの値（「140万円」「20歳」）に置き換える。アプリの RuleEngine.fillIn と同じ表記。"""

    def value(m: re.Match[str]) -> str:
        field = m.group(1)
        v = getattr(profile, field, None)
        if field == "age_at_year_end" and v is None:
            v = profile.age
        if v is None:
            return "（未入力）"
        if field == "annual_income":
            return f"{v // 10000:,}万円"
        if field in ("age", "age_at_year_end"):
            return f"{v}歳"
        if field == "dependents":
            return f"{v}人"
        labels = _choice_labels().get(field, {})
        if isinstance(v, list):
            return "、".join(labels.get(x, x) for x in v) or "なし"
        return labels.get(v, str(v))

    return _PLACEHOLDER.sub(value, text)


def recommend(profile: UserProfile) -> list[Recommendation]:
    """必須のものと、関係がありそうなもの（必須でも対象外でもなく、点数の合計がしきい値以上）を返す。必須を先に、それぞれ点数の高い順。

    判定の意味はアプリの RuleEngine.kt と同じ（ここではプロフィールがすべて分かっている前提）。
    - required の行か、when をすべて満たした conditional の行があれば必須
    - そうでなく、unrelated の行が当てはまれば（when があればすべて満たせば）対象外（返さない）
    """
    results: list[Recommendation] = []
    for proc in load_procedures().values():
        counted = [r for r in proc.relations if _check(r, profile) and all(_check(c, profile) for c in r.when)]
        required = any(r.flag == "required" or (r.flag == "conditional" and r.when) for r in counted)
        excluded = any(r.flag == "unrelated" for r in counted)
        score = round(sum(r.score for r in counted), 3)
        if not required and (excluded or score < THRESHOLD):
            continue
        reasons = [fill_in(r.reason, profile) for r in sorted(counted, key=lambda r: -r.score) if r.reason and r.score > 0]
        results.append(
            Recommendation(
                procedure_id=proc.id,
                title=proc.title,
                category=proc.category,
                summary=proc.summary,
                required=required,
                score=score,
                deadline=proc.deadline,
                reasons=list(dict.fromkeys(reasons)),
            )
        )
    results.sort(key=lambda r: (not r.required, -r.score))
    return results
