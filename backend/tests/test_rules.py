from fastapi.testclient import TestClient

from app.main import app
from app.rules.engine import load_procedures, recommend
from app.schemas import UserProfile

client = TestClient(app)


def ids(profile: UserProfile) -> list[str]:
    return [r.procedure_id for r in recommend(profile)]


def by_id(profile: UserProfile) -> dict:
    return {r.procedure_id: r for r in recommend(profile)}


def test_all_procedure_files_load():
    assert len(load_procedures()) >= 10


def test_resigned_person_gets_required_and_likely_procedures():
    rec = by_id(UserProfile(age=28, occupation="unemployed", annual_income=0, life_events=["resign"]))
    # 必須: 国民年金（条件次第の条件を満たした）・退職後の住民税
    assert rec["kokumin_nenkin"].required and rec["juminzei_resign"].required
    # 関係がありそう: 国保（任意継続・家族の扶養も選べるので必須にはしない）
    assert not rec["kokuho_enroll"].required and rec["kokuho_enroll"].score >= 1.0


def test_required_comes_first_then_by_score():
    result = recommend(UserProfile(age=28, occupation="unemployed", annual_income=0, life_events=["resign"]))
    flags = [r.required for r in result]
    assert flags == sorted(flags, reverse=True)
    likely = [r.score for r in result if not r.required]
    assert likely == sorted(likely, reverse=True)


def test_conditional_row_is_not_counted_when_condition_fails():
    # 65歳で退職しても国民年金の加入は無い（60歳まで）。点数にも数えない
    assert "kokumin_nenkin" not in ids(UserProfile(age=65, occupation="unemployed", annual_income=0, life_events=["resign"]))


def test_unrelated_row_lowers_score():
    # 会社員を兼ねるフリーランスは、国保の点数が 0.6 - 1.0 でしきい値を下回る
    assert "kokuho_enroll" not in ids(UserProfile(age=30, occupation=["employee", "freelance"], annual_income=0))
    assert "kokuho_enroll" in ids(UserProfile(age=30, occupation=["freelance"], annual_income=0))


def test_student_gets_tokurei_only_after_20():
    assert "gakusei_tokurei" in ids(UserProfile(age=20, occupation="student", annual_income=0))
    assert "gakusei_tokurei" not in ids(UserProfile(age=19, occupation="student", annual_income=0))


def test_employee_without_events_only_sees_year_end_adjustment():
    rec = recommend(UserProfile(age=35, occupation="employee", annual_income=5_000_000, health_insurance="employer"))
    assert [(r.procedure_id, r.required) for r in rec] == [("nenmatsu_chosei", False)]


def test_reasons_are_collected():
    rec = recommend(UserProfile(age=30, occupation="freelance", annual_income=4_000_000, life_events=["start_business"]))
    kaigyo = next(r for r in rec if r.procedure_id == "kaigyo_todoke")
    assert kaigyo.required and len(kaigyo.reasons) == 2


def test_multiple_occupations():
    # 会社員でフリーランスの副業もしている人は、確定申告は必要だが国保・国民年金への加入は不要
    result = ids(
        UserProfile(age=30, occupation=["employee", "freelance"], annual_income=5_000_000, health_insurance="employer")
    )
    assert "kakutei_shinkoku" in result
    assert "kokuho_enroll" not in result and "kokumin_nenkin" not in result
    # 学生でアルバイトもしている20歳は学生納付特例の対象
    assert "gakusei_tokurei" in ids(UserProfile(age=20, occupation=["student", "part_time"], annual_income=800_000))


def test_api_recommendations_without_llm():
    res = client.post("/api/recommendations", json={"age": 22, "occupation": "student", "annual_income": 800000})
    assert res.status_code == 200
    body = res.json()
    assert body["items"][0]["procedure_id"] == "gakusei_tokurei"


def test_api_procedure_detail():
    res = client.get("/api/procedures/kokuho_enroll")
    assert res.status_code == 200
    assert len(res.json()["steps"]) > 0
    assert "relations" not in res.json()
    assert client.get("/api/procedures/nope").status_code == 404
