from typing import get_args

from fastapi.testclient import TestClient

from app.main import app
from app.rules.engine import load_procedures
from app.rules.questions import QuestionDef, build_catalog, load_question_defs, range_choices
from app.schemas import UserProfile

client = TestClient(app)


def literal_values(field: str) -> set[str]:
    """UserProfile の Literal（list[Literal] も含む）が取りうる値。"""
    tp = UserProfile.model_fields[field].annotation
    while get_args(tp) and not all(isinstance(a, str) for a in get_args(tp)):
        tp = get_args(tp)[0]
    return set(get_args(tp))


def test_every_field_used_in_rules_has_a_question():
    used = {c.field for p in load_procedures().values() for r in p.relations for c in (r, *r.when)}
    asked = {f for q in build_catalog().questions for f in [q.field, *q.provides]}
    assert used <= asked


def test_choice_values_are_valid():
    for q in load_question_defs():
        if q.kind in ("single", "multi"):
            assert {c.value for c in q.choices} <= literal_values(q.field), q.field


def test_every_question_has_a_short_label():
    for q in build_catalog().questions:
        assert q.label and len(q.label) <= 15, q.field


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


def test_income_and_dependents_are_numbers():
    qs = {q.field: q for q in build_catalog().questions}
    assert (qs["annual_income"].kind, qs["annual_income"].unit, qs["annual_income"].scale) == ("number", "万円", 10000)
    assert (qs["dependents"].kind, qs["dependents"].unit) == ("number", "人")


def test_range_choices_follow_rule_thresholds():
    # range にすると、手続き YAML の条件の境目（150万円以上・2,000万円超）だけで選択肢を作る
    income = QuestionDef(field="annual_income", kind="range", text="", label="年収", unit="万円", scale=10000)
    assert [c.label for c in range_choices(income, load_procedures().values())] == ["150万円未満", "150〜2,000万円", "2,000万円超"]
    deps = QuestionDef(field="dependents", kind="range", text="", label="扶養", unit="人")
    assert [c.label for c in range_choices(deps, load_procedures().values())] == ["0人", "1人以上"]


def test_range_choices_cover_all_values_without_gaps():
    for field in ["annual_income", "dependents"]:
        q = QuestionDef(field=field, kind="range", text="", label="")
        choices = range_choices(q, load_procedures().values())
        assert choices[0].min == 0 and choices[-1].max is None
        for a, b in zip(choices, choices[1:]):
            assert a.max + 1 == b.min


def test_unused_fields_are_not_asked():
    assert "has_spouse" not in {q.field for q in build_catalog().questions}


def test_api_catalog():
    res = client.get("/api/catalog")
    assert res.status_code == 200
    body = res.json()
    assert len(body["procedures"]) == len(load_procedures())
    assert all(p["relations"] and p["steps"] for p in body["procedures"])
    assert body["threshold"] == 0.5
    assert {q["field"] for q in body["questions"]} >= {"life_events", "occupation", "birth_date"}
