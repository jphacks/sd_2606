from fastapi.testclient import TestClient

from app.main import app
from app.rules.engine import load_procedures, recommend
from app.schemas import UserProfile

client = TestClient(app)


def ids(profile: UserProfile) -> list[str]:
    return [r.procedure_id for r in recommend(profile)]


def test_all_procedure_files_load():
    assert len(load_procedures()) >= 10


def test_resigned_employee_gets_insurance_and_pension():
    result = ids(UserProfile(age=28, occupation="unemployed", annual_income=0, life_events=["resign"]))
    assert {"kokuho_enroll", "kokumin_nenkin", "juminzei_resign"} <= set(result)
    assert result[0] == "kokuho_enroll"  # 優先度が最も高い


def test_student_gets_tokurei_only_after_20():
    assert "gakusei_tokurei" in ids(UserProfile(age=20, occupation="student", annual_income=0))
    assert "gakusei_tokurei" not in ids(UserProfile(age=19, occupation="student", annual_income=0))


def test_employee_without_events_gets_nothing_special():
    assert ids(UserProfile(age=35, occupation="employee", annual_income=5_000_000, health_insurance="employer")) == []


def test_reasons_are_collected():
    rec = recommend(UserProfile(age=30, occupation="freelance", annual_income=4_000_000, life_events=["start_business"]))
    kaigyo = next(r for r in rec if r.procedure_id == "kaigyo_todoke")
    assert len(kaigyo.reasons) == 2


def test_api_recommendations_without_llm():
    res = client.post("/api/recommendations", json={"age": 22, "occupation": "student", "annual_income": 800000})
    assert res.status_code == 200
    body = res.json()
    assert body["items"][0]["procedure_id"] == "gakusei_tokurei"


def test_api_procedure_detail():
    res = client.get("/api/procedures/kokuho_enroll")
    assert res.status_code == 200
    assert len(res.json()["steps"]) > 0
    assert "match" not in res.json()
    assert client.get("/api/procedures/nope").status_code == 404
