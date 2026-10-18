from fastapi.testclient import TestClient

from app.main import app
from app.rules.engine import load_procedures, recommend
from app.schemas import UserProfile

client = TestClient(app)


def student(age: int = 20, income: int = 0, part_time: str = "yes", supported_by: str = "parent", **kw) -> UserProfile:
    """アプリの対象の学生。年末の年齢は、省けば今日の年齢と同じ。"""
    kw.setdefault("age_at_year_end", age)
    return UserProfile(age=age, annual_income=income, part_time=part_time, supported_by=supported_by, **kw)


def ids(profile: UserProfile) -> list[str]:
    return [r.procedure_id for r in recommend(profile)]


def by_id(profile: UserProfile) -> dict:
    return {r.procedure_id: r for r in recommend(profile)}


def test_procedures_are_for_students():
    # 学生に関係する手続きだけを配信する（会社員・自営業・退職者向けは data/archive/）
    assert set(load_procedures()) == {
        "nenshu_kabe", "fuyou_hazureru", "kokumin_nenkin", "kakutei_shinkoku", "nenmatsu_chosei", "kokuho_enroll", "hikkoshi",
    }


def test_every_procedure_has_deadline_for_sorting():
    assert all(p.due is not None for p in load_procedures().values())


def test_required_comes_first_then_by_score():
    result = recommend(student(age=20, income=1_600_000))
    flags = [r.required for r in result]
    assert flags == sorted(flags, reverse=True)
    likely = [r.score for r in result if not r.required]
    assert likely == sorted(likely, reverse=True)


def test_nenkin_is_required_from_20():
    # 今年20歳になる学生は、誕生日の前でも国民年金が必須（学生納付特例を使うか納めるかはフローで選ぶ）
    assert by_id(student(age=19, age_at_year_end=20))["kokumin_nenkin"].required
    assert by_id(student(age=21))["kokumin_nenkin"].required
    # 年末でも19歳なら対象外
    assert "kokumin_nenkin" not in ids(student(age=18, age_at_year_end=19))


def test_part_time_decides_year_end_adjustment_and_tax_return():
    assert by_id(student(income=800_000))["nenmatsu_chosei"].score > 0
    no_job = ids(student(income=0, part_time="no"))
    assert "nenmatsu_chosei" not in no_job and "kakutei_shinkoku" not in no_job and "nenshu_kabe" not in no_job


def test_parent_dependency_threshold_depends_on_age_at_year_end():
    def hazureru(age: int, income: int):
        return by_id(student(age=age, income=income)).get("fuyou_hazureru")

    # 19〜22歳: 136万円以下なら対象外（扶養のまま）、136万円を超えると親の税金の控除が変わる、150万円以上で健康保険も外れる
    assert hazureru(20, 1_300_000) is None
    assert hazureru(20, 1_400_000).required
    assert hazureru(20, 1_500_000).required
    # それ以外: 130万円未満なら対象外、130万円以上で健康保険の扶養から外れる
    assert hazureru(18, 1_250_000) is None
    assert hazureru(18, 1_300_000).required


def test_national_health_insurance_after_leaving_parent():
    assert "kokuho_enroll" not in ids(student(age=20, income=1_400_000))
    assert by_id(student(age=20, income=1_500_000))["kokuho_enroll"].required
    # すでに国保に入っていれば対象外
    assert "kokuho_enroll" not in ids(student(age=20, income=1_500_000, health_insurance="national"))


def test_multiple_jobs_makes_tax_return_required_only_when_income_tax_applies():
    # 国税庁 No.1900: 掛け持ちで、年末調整を受けない方の給与が20万円超なら申告が要る。所得税がかかる178万円超のときだけ
    base = dict(age=20, age_at_year_end=20, part_time="yes", annual_income=2_000_000, supported_by="parent")
    assert not by_id(UserProfile(**base))["kakutei_shinkoku"].required
    rec = by_id(UserProfile(**base, multiple_jobs="yes", side_job_over_200k="yes"))["kakutei_shinkoku"]
    assert rec.required and any("掛け持ち" in r for r in rec.reasons)
    assert not by_id(UserProfile(**base, multiple_jobs="yes", side_job_over_200k="no"))["kakutei_shinkoku"].required
    assert not by_id(UserProfile(**base, multiple_jobs="no"))["kakutei_shinkoku"].required
    # 178万円以下なら所得税がかからないので、掛け持ちでも義務にはならない（還付申告で戻ることはある）
    low = by_id(UserProfile(**{**base, "annual_income": 1_300_000}, multiple_jobs="yes", side_job_over_200k="yes"))
    assert not low["kakutei_shinkoku"].required
    assert any("戻る" in r for r in low["kakutei_shinkoku"].reasons)


def test_income_wall_is_required_only_when_parent_must_act():
    def kabe(income: int):
        return by_id(student(age=20, income=income))["nenshu_kabe"]

    # 基準より低ければ、やることは無いので「関係がありそう」
    assert not kabe(800_000).required
    # 136万円を超えると親の税金の控除が変わり、親が申告し直す
    assert kabe(1_400_000).required


def test_unrelated_reasons_are_not_shown_as_reasons():
    rec = by_id(student(age=22, income=800_000))
    # {part_time} {supported_by} は回答の表記に置き換える
    assert rec["nenshu_kabe"].reasons == ["アルバイト している", "親の扶養に入っている"]


def test_reasons_are_filled_with_the_profile():
    rec = by_id(student(age=20, income=1_600_000))
    assert "年収 160万円（150万円以上）、年末の年齢 20歳（19〜22歳）" in rec["fuyou_hazureru"].reasons
    assert "年収 160万円（119万円超。あなたに住民税がかかる）" in rec["nenshu_kabe"].reasons
    assert all("{" not in r for item in rec.values() for r in item.reasons)


def test_api_recommendations_without_llm():
    res = client.post(
        "/api/recommendations",
        json={"age": 20, "age_at_year_end": 20, "part_time": "yes", "supported_by": "parent", "annual_income": 800000},
    )
    assert res.status_code == 200
    assert res.json()["items"][0]["procedure_id"] == "kokumin_nenkin"


def test_api_procedure_detail():
    res = client.get("/api/procedures/kokuho_enroll")
    assert res.status_code == 200
    assert len(res.json()["steps"]) > 0
    assert "relations" not in res.json()
    assert client.get("/api/procedures/nope").status_code == 404
