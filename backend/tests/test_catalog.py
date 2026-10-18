from typing import get_args

from fastapi.testclient import TestClient

from app.main import app
from app.rules.engine import all_steps, load_procedures
from app.rules.questions import QuestionDef, build_catalog, load_question_defs, range_choices
from app.schemas import UserProfile

client = TestClient(app)


def literal_values(field: str) -> set[str]:
    """UserProfile の Literal（list[Literal] も含む）が取りうる値。"""
    tp = UserProfile.model_fields[field].annotation
    while get_args(tp) and not all(isinstance(a, str) for a in get_args(tp)):
        tp = get_args(tp)[0]
    return set(get_args(tp))


def choice_fields(p) -> set[str]:
    return {s.choice.field for s in all_steps(p.steps) if s.choice}


def test_every_field_used_in_rules_has_a_question():
    used = {c.field for p in load_procedures().values() for r in p.relations for c in (r, *r.when)}
    used |= {c.field for p in load_procedures().values() for s in all_steps(p.steps) for c in s.when}  # ステップの表示条件
    asked = {f for q in build_catalog().questions for f in [q.field, *q.provides]}
    assert used <= asked


def test_choice_values_are_valid():
    for q in load_question_defs():
        if q.kind in ("single", "multi"):
            assert {c.value for c in q.choices} <= literal_values(q.field), q.field


def test_every_question_has_a_short_label():
    for q in build_catalog().questions:
        assert q.label and len(q.label) <= 15, q.field


def test_basic_questions_are_required_and_others_optional():
    # 最初の画面で必ず答えてもらう基本情報。ほかは任意（必要になった手続きのカードでも聞く）
    # アプリの対象は学生なので、働き方は聞かずアルバイトをしているかだけを聞く
    required = [q.field for q in build_catalog().questions if q.required]
    assert required == ["part_time", "birth_date", "supported_by", "annual_income", "income_basis"]
    optional = {q.field for q in build_catalog().questions if not q.required and not q.inline and not q.part_of}
    assert optional == {"health_insurance"}


def test_inline_questions_are_yes_no_and_used_by_some_procedure():
    # カードの中でだけ聞く質問は「はい / いいえ」で、どれかの手続きの判定に使われている
    used = {c.field for p in load_procedures().values() for r in p.relations for c in (r, *r.when)}
    inline = [q for q in build_catalog().questions if q.inline]
    assert [q.field for q in inline] == ["multiple_jobs", "year_end_employed", "moving_plan", "side_job_over_200k"]
    for q in inline:
        assert not q.required and q.field in used
        assert [c.value for c in q.choices] == ["yes", "no"]


def test_required_single_choice_questions_offer_unknown():
    # 必須でも「わからない」を選べる（扶養は税金と健康保険で意味が違い、分からない人が多い）
    for q in build_catalog().questions:
        # アルバイトの有無と、年収の見込み / 確定（必ず選ぶ2択）は本人が分かる
        if q.required and q.kind == "single" and q.field not in ("part_time", "income_basis"):
            assert "unknown" in {c.value for c in q.choices}, q.field


def test_step_placeholders_refer_to_answer_fields():
    import re

    fields = {f for q in build_catalog().questions for f in [q.field, *q.provides]}
    for p in load_procedures().values():
        for r in p.relations:
            for name in re.findall(r"\{(\w+)\}", r.reason or ""):
                assert name in fields, (p.id, name)
        for s in all_steps(p.steps):
            for name in re.findall(r"\{(\w+)\}", s.title + s.detail + "".join(s.details)):
                assert name in fields, (p.id, name)


def test_part_time_is_yes_or_no():
    qs = {q.field: q for q in build_catalog().questions}
    assert qs["part_time"].kind == "single" and [c.value for c in qs["part_time"].choices] == ["yes", "no"]
    assert "occupation" not in qs


def test_age_is_computed_from_birth_date_on_device():
    qs = {q.field: q for q in build_catalog().questions}
    assert qs["birth_date"].kind == "date"
    assert qs["birth_date"].provides == ["age", "age_at_year_end"]
    assert "age" not in qs  # 年齢そのものは聞かない


def test_income_is_a_number():
    qs = {q.field: q for q in build_catalog().questions}
    assert (qs["annual_income"].kind, qs["annual_income"].unit, qs["annual_income"].scale) == ("number", "万円", 10000)


def test_range_choices_follow_rule_thresholds():
    # range にすると、手続き YAML の条件の境目（130万円以上・150万円以上・178万円超など）だけで選択肢を作る
    income = QuestionDef(field="annual_income", kind="range", text="", label="年収", unit="万円", scale=10000)
    choices = range_choices(income, load_procedures().values())
    assert {1_300_000, 1_500_000, 1_780_001} <= {c.min for c in choices}
    assert "130万円以上150万円未満" not in [c.label for c in choices]  # 136万円にも境目がある
    assert not any("9999" in c.label for c in choices)  # 「150万円未満」のように書く


def test_range_choices_cover_all_values_without_gaps():
    for field in ["annual_income"]:
        q = QuestionDef(field=field, kind="range", text="", label="")
        choices = range_choices(q, load_procedures().values())
        assert choices[0].min == 0 and choices[-1].max is None
        for a, b in zip(choices, choices[1:]):
            assert a.max + 1 == b.min


def test_reminders_have_valid_dates():
    reminders = [r for p in load_procedures().values() for r in p.reminders]
    assert len(reminders) >= 5
    for r in reminders:
        assert r.label and 0 <= r.before_days <= 60
    # 毎月の通知（国民年金の保険料など）と、毎年の通知（確定申告など）の両方がある
    assert any(r.month is None for r in reminders) and any(r.month == 3 and r.day == 15 for r in reminders)


def test_reminder_conditions_use_known_fields():
    fields = {f for q in build_catalog().questions for f in [q.field, *q.provides]}
    for p in load_procedures().values():
        for r in p.reminders:
            assert {c.field for c in r.when} <= fields | choice_fields(p), p.id


def test_nenkin_flow_branches_into_tokurei_or_paying():
    # 国民年金は「学生納付特例を使う／保険料を納める」の分岐。選んだ先にだけステップと金額がある
    choice = next(s.choice for s in load_procedures()["kokumin_nenkin"].steps if s.choice)
    assert choice.field == "nenkin_plan"
    assert [o.value for o in choice.options] == ["tokurei", "pay"]
    assert all(o.label and o.detail and o.steps and o.amounts for o in choice.options)


def test_every_procedure_has_one_card_amount_at_most():
    for p in load_procedures().values():
        assert all(a.kind for a in p.amounts)
        # 一覧のカードには代表的な金額を1つだけ出す（当てはまるもののうち最初の1つ）
        assert sum(a.card for a in p.amounts if not a.when) <= 1, p.id


def test_unused_fields_are_not_asked():
    assert "has_spouse" not in {q.field for q in build_catalog().questions}


def test_api_catalog():
    res = client.get("/api/catalog")
    assert res.status_code == 200
    body = res.json()
    assert len(body["procedures"]) == len(load_procedures())
    assert all(p["relations"] and p["steps"] for p in body["procedures"])
    assert body["threshold"] is None  # 下限なし（-inf）。対象外でなければすべて出す
    assert {q["field"] for q in body["questions"]} >= {"part_time", "birth_date"}
    assert "life_events" not in {q["field"] for q in body["questions"]}


def test_income_basis_is_asked_with_annual_income():
    # 年収の「見込み / 確定」は年収の欄の中で必ず選ぶ。手続き YAML の条件には使わない（見込みの幅はアプリが判定に入れる）
    qs = {q.field: q for q in build_catalog().questions}
    assert qs["income_basis"].part_of == "annual_income" and qs["income_basis"].required
    assert [c.value for c in qs["income_basis"].choices] == ["estimate", "actual"]
    used = {c.field for p in load_procedures().values() for r in p.relations for c in (r, *r.when)}
    assert "income_basis" not in used
