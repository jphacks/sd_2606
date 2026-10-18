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


def test_every_procedure_has_deadline_for_sorting_or_none():
    # 期限順の並べ替え: due は days か month（と day）のどちらか。期限が書いてあるものの多くに due を付けている
    procs = load_procedures().values()
    assert sum(p.due is not None for p in procs) >= 12


def test_unemployed_person_gets_required_and_likely_procedures():
    rec = by_id(UserProfile(age=28, age_at_year_end=28, occupation="unemployed", annual_income=0))
    # 必須: 国民年金（条件次第の条件をすべて満たした）
    assert rec["kokumin_nenkin"].required
    # 関係がありそう: 国保（任意継続・家族の扶養も選べるので必須にはしない）
    assert not rec["kokuho_enroll"].required and rec["kokuho_enroll"].score >= 0.7
    assert {"juminzei_resign", "nini_keizoku", "fuyou_hairu", "nenkin_menjo", "kakutei_shinkoku"} <= rec.keys()


def test_required_comes_first_then_by_score():
    result = recommend(UserProfile(age=28, occupation="unemployed", annual_income=0))
    flags = [r.required for r in result]
    assert flags == sorted(flags, reverse=True)
    likely = [r.score for r in result if not r.required]
    assert likely == sorted(likely, reverse=True)


def test_unrelated_row_excludes_procedure():
    # 会社員は国保・国民年金・任意継続の対象外
    result = ids(UserProfile(age=30, occupation="employee", annual_income=5_000_000, health_insurance="employer"))
    assert not {"kokuho_enroll", "kokumin_nenkin", "nini_keizoku"} & set(result)


def test_threshold_is_minus_infinity_so_zero_score_procedures_are_listed():
    # 関係する行が1つも無い（点数 0）手続きも、対象外でなければ出す
    rec = by_id(UserProfile(age=40, occupation="employee", annual_income=5_000_000, health_insurance="employer"))
    assert rec["hikkoshi"].score == 0 and not rec["hikkoshi"].required


def test_age_turning_20_this_year():
    # 今年20歳になる学生: 国民年金と学生納付特例が必須（誕生日の前でも）
    rec = by_id(UserProfile(age=19, age_at_year_end=20, occupation="student", annual_income=0))
    assert rec["kokumin_nenkin"].required and rec["gakusei_tokurei"].required
    # 年末でも19歳なら国民年金は対象外
    assert "kokumin_nenkin" not in ids(UserProfile(age=18, age_at_year_end=19, occupation="student", annual_income=0))


def test_conditional_row_is_not_counted_when_condition_fails():
    # 65歳の無職: 国民年金の加入は60歳まで（対象外）
    assert "kokumin_nenkin" not in ids(UserProfile(age=65, age_at_year_end=65, occupation="unemployed", annual_income=0))


def test_student_gets_tokurei_only_after_20():
    assert by_id(UserProfile(age=20, age_at_year_end=20, occupation="student", annual_income=0))["gakusei_tokurei"].required
    assert "gakusei_tokurei" not in ids(UserProfile(age=18, age_at_year_end=18, occupation="student", annual_income=0))


def test_parent_dependency_threshold_depends_on_age_at_year_end():
    def hazureru(age: int, income: int):
        return by_id(
            UserProfile(age=age, age_at_year_end=age, occupation="part_time", annual_income=income, supported_by="parent")
        ).get("fuyou_hazureru")

    # 19〜22歳: 136万円以下なら対象外（扶養のまま）、136万円を超えると親の税金の控除が変わる、150万円以上で健康保険も外れる
    assert hazureru(20, 1_300_000) is None
    assert hazureru(20, 1_400_000).required
    assert hazureru(20, 1_500_000).required
    # それ以外: 130万円未満なら対象外、130万円以上で健康保険の扶養から外れる
    assert hazureru(24, 1_250_000) is None
    assert hazureru(24, 1_300_000).required


def test_employee_gets_year_end_adjustment_as_required():
    rec = by_id(UserProfile(age=35, occupation="employee", annual_income=5_000_000, health_insurance="employer"))
    assert rec["nenmatsu_chosei"].required


def test_multiple_occupations():
    # 会社員でフリーランスの副業もしている人は、確定申告は必要だが国保・国民年金への加入は不要
    result = ids(
        UserProfile(age=30, occupation=["employee", "freelance"], annual_income=5_000_000, health_insurance="employer")
    )
    assert "kakutei_shinkoku" in result
    assert "kokuho_enroll" not in result and "kokumin_nenkin" not in result
    # 学生でアルバイトもしている20歳は学生納付特例の対象
    assert "gakusei_tokurei" in ids(UserProfile(age=20, occupation=["student", "part_time"], annual_income=800_000))


def test_multiple_jobs_makes_tax_return_required():
    # 掛け持ちのバイト（年収130万円）は、カードの質問に「はい」と答えると確定申告が必要になる
    base = dict(age=20, age_at_year_end=20, occupation=["student", "part_time"], annual_income=1_300_000, supported_by="parent")
    assert not by_id(UserProfile(**base))["kakutei_shinkoku"].required
    rec = by_id(UserProfile(**base, multiple_jobs="yes"))["kakutei_shinkoku"]
    assert rec.required and any("掛け持ち" in r for r in rec.reasons)
    assert not by_id(UserProfile(**base, multiple_jobs="no"))["kakutei_shinkoku"].required
    # 収入が無ければ「はい」でも必要にしない
    no_income = by_id(UserProfile(**{**base, "annual_income": 0}, multiple_jobs="yes"))
    assert "kakutei_shinkoku" not in no_income or not no_income["kakutei_shinkoku"].required


def test_unrelated_reasons_are_not_shown_as_reasons():
    rec = by_id(UserProfile(age=22, age_at_year_end=22, occupation="student", annual_income=800_000, supported_by="parent"))
    assert all("ため" in r for r in rec["nenshu_kabe"].reasons)
    assert len(rec["nenshu_kabe"].reasons) == 2  # 学生・家族の扶養


def test_income_wall_is_required_only_when_family_must_act():
    def kabe(income: int):
        return by_id(
            UserProfile(age=20, age_at_year_end=20, occupation="part_time", annual_income=income, supported_by="parent")
        )["nenshu_kabe"]

    # 基準より低ければ、やることは無いので「関係がありそう」
    assert not kabe(800_000).required
    # 136万円を超えると親の税金の控除が変わり、親が申告し直す
    assert kabe(1_400_000).required


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
