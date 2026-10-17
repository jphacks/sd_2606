"""制度データ（YAML）とユーザープロフィールを突き合わせ、該当する手続きを返すルールエンジン。

YAML の書き方は docs/procedures.md を参照。
"""

from collections.abc import Callable
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml

from app.schemas import Condition, ProcedureDef, Recommendation, UserProfile

DATA_DIR = Path(__file__).resolve().parents[2] / "data" / "procedures"

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


# 複数選択の項目（life_events・occupation）では、eq / in は「選んだどれかが当てはまる」、
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
        for clause in proc.match:
            for cond in clause.when:
                if cond.field not in UserProfile.model_fields:
                    raise ValueError(f"{path.name}: unknown field {cond.field}")
                if cond.op not in OPS:
                    raise ValueError(f"{path.name}: unknown op {cond.op}")
        procs[proc.id] = proc
    return procs


def recommend(profile: UserProfile) -> list[Recommendation]:
    results: list[Recommendation] = []
    for proc in load_procedures().values():
        reasons = [c.reason for c in proc.match if all(_check(w, profile) for w in c.when)]
        if not reasons:
            continue
        results.append(
            Recommendation(
                procedure_id=proc.id,
                title=proc.title,
                category=proc.category,
                summary=proc.summary,
                priority=proc.priority,
                deadline=proc.deadline,
                reasons=list(dict.fromkeys(reasons)),
            )
        )
    results.sort(key=lambda r: r.priority, reverse=True)
    return results
