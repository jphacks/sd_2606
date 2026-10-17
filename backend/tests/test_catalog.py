from typing import get_args

from fastapi.testclient import TestClient

from app.main import app
from app.rules.engine import load_procedures
from app.rules.questions import build_catalog, load_question_defs
from app.schemas import UserProfile

client = TestClient(app)


def labels(field: str) -> list[str]:
    q = next(q for q in build_catalog().questions if q.field == field)
    return [c.label for c in q.choices]


def literal_values(field: str) -> set[str]:
    """UserProfile の Literal（list[Literal] も含む）が取りうる値。"""
    tp = UserProfile.model_fields[field].annotation
    while get_args(tp) and not all(isinstance(a, str) for a in get_args(tp)):
        tp = get_args(tp)[0]
    return set(get_args(tp))


def test_every_field_used_in_rules_has_a_question():
    used = {c.field for p in load_procedures().values() for m in p.match for c in m.when}
    asked = {f for q in build_catalog().questions for f in [q.field, *q.provides]}
    assert used <= asked


def test_choice_values_are_valid():
    for q in load_question_defs():
        if q.kind in ("single", "multi"):
            assert {c.value for c in q.choices} <= literal_values(q.field), q.field


def test_only_minimal_questions_are_asked_first():
    initial = [q.field for q in build_catalog().questions if q.initial]
    assert initial == ["life_events", "occupation"]


def test_occupation_is_multi_and_only_life_events_mean_none_when_empty():
    qs = {q.field: q for q in build_catalog().questions}
    assert qs["occupation"].kind == "multi" and not qs["occupation"].none_if_empty
    assert qs["life_events"].none_if_empty


def test_age_is_computed_from_birth_date_on_device():
    qs = {q.field: q for q in build_catalog().questions}
    assert qs["birth_date"].kind == "date"
    assert qs["birth_date"].provides == ["age", "age_at_year_end"]
    assert "age" not in qs  # 年齢そのものは聞かない


def test_range_choices_follow_rule_thresholds():
    # 年収は「2,000万円を超えるか」だけ、扶養家族は「いるか」だけを聞く
    assert labels("annual_income") == ["2,000万円以下", "2,000万円超"]
    assert labels("dependents") == ["0人", "1人以上"]


def test_range_choices_cover_all_values_without_gaps():
    for q in build_catalog().questions:
        if q.kind != "range":
            continue
        assert q.choices[0].min == 0 and q.choices[-1].max is None
        for a, b in zip(q.choices, q.choices[1:]):
            assert a.max + 1 == b.min


def test_unused_fields_are_not_asked():
    assert "has_spouse" not in {q.field for q in build_catalog().questions}


def test_api_catalog():
    res = client.get("/api/catalog")
    assert res.status_code == 200
    body = res.json()
    assert len(body["procedures"]) == len(load_procedures())
    assert all(p["match"] and p["steps"] for p in body["procedures"])
    assert {q["field"] for q in body["questions"]} >= {"life_events", "occupation", "birth_date"}
