package jp.jphacks.sd2606.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {
    // backend/data/procedures の書き方と同じ形の、小さな制度データ
    private val catalog = Json.decodeFromString<Catalog>(
        """
        {
          "threshold": 0.5,
          "procedures": [
            {"id": "nenkin", "title": "国民年金", "category": "pension", "summary": "", "relations": [
              {"field": "life_events", "value": "turn_20", "flag": "required", "score": 1.0, "reason": "20歳"},
              {"field": "life_events", "value": "resign", "flag": "conditional", "score": 0.8, "reason": "退職して20〜59歳",
               "when": [{"field": "age", "op": "gte", "value": 20}, {"field": "age", "op": "lt", "value": 60},
                        {"field": "occupation", "op": "ne", "value": "employee"}]},
              {"field": "occupation", "value": "employee", "flag": "unrelated", "score": -1.0}]},
            {"id": "kokuho", "title": "国保", "category": "insurance", "summary": "", "relations": [
              {"field": "life_events", "value": "resign", "flag": "conditional", "score": 1.0, "reason": "退職（3択）"},
              {"field": "occupation", "op": "in", "value": ["freelance"], "flag": "conditional", "score": 0.6, "reason": "フリーランス"},
              {"field": "occupation", "value": "employee", "flag": "unrelated", "score": -1.0}]},
            {"id": "kakutei", "title": "確定申告", "category": "tax", "summary": "", "relations": [
              {"field": "occupation", "op": "in", "value": ["freelance"], "flag": "required", "score": 0.7, "reason": "フリーランス"},
              {"field": "life_events", "value": "resign", "flag": "conditional", "score": 0.4, "reason": "退職（還付）"},
              {"field": "occupation", "value": "employee", "flag": "conditional", "score": 0.3, "reason": "高収入の会社員",
               "when": [{"field": "annual_income", "op": "gt", "value": 20000000}]}]},
            {"id": "nenkin_kojo", "title": "公的年金等控除", "category": "tax", "summary": "", "relations": [
              {"field": "age_at_year_end", "op": "gte", "value": 65, "flag": "required", "score": 0.5, "reason": "年末時点で65歳以上"}]}
          ],
          "questions": [
            {"field": "birth_date", "kind": "date", "text": "生年月日は？", "label": "生年月日", "provides": ["age", "age_at_year_end"]},
            {"field": "annual_income", "kind": "range", "text": "年収は？", "label": "年収", "choices": [
              {"label": "2,000万円以下", "min": 0, "max": 20000000}, {"label": "2,000万円超", "min": 20000001}]}
          ]
        }
        """,
    )

    private val today = LocalDate.of(2026, 10, 17)

    private fun eval(vararg answers: Pair<String, Answer>) =
        RuleEngine.evaluate(catalog.procedures, answers.toMap(), catalog.threshold, today)

    private val resigned = "life_events" to Answer.Multi(listOf("resign"))
    private val unemployed = "occupation" to Answer.Multi(listOf("unemployed"))

    @Test
    fun requiredRowMakesProcedureRequired() {
        assertEquals(Tri.TRUE, eval("life_events" to Answer.Multi(listOf("turn_20"))).statusOf("nenkin"))
    }

    @Test
    fun conditionalWithUnknownConditionIsLikelyAndAsksForIt() {
        val nenkin = eval(resigned, unemployed).find("nenkin")!!
        assertEquals(false, nenkin.required)
        assertEquals(0.8, nenkin.score, 1e-9)
        assertEquals(listOf("age"), nenkin.missing)
        assertEquals("birth_date", RuleEngine.questionFor("age", catalog.questions)?.field)
    }

    @Test
    fun conditionalBecomesRequiredWhenAllConditionsHold() {
        assertEquals(Tri.TRUE, eval(resigned, unemployed, "birth_date" to Answer.Date("1998-04-01")).statusOf("nenkin"))
    }

    @Test
    fun conditionalRowIsNotCountedWhenAConditionFails() {
        // 65歳で退職: 国民年金の行は数えず、点数 0 で一覧に出ない
        assertNull(eval(resigned, unemployed, "birth_date" to Answer.Date("1961-01-01")).find("nenkin"))
    }

    @Test
    fun conditionalWithoutConditionsStaysLikely() {
        val kokuho = eval(resigned, unemployed).find("kokuho")!!
        assertEquals(false, kokuho.required)
        assertEquals(emptyList<String>(), kokuho.missing)
    }

    @Test
    fun belowThresholdIsHidden() {
        // 退職だけでは確定申告は 0.4 でしきい値 0.5 未満
        assertNull(eval(resigned, unemployed).find("kakutei"))
    }

    @Test
    fun unrelatedRowLowersScore() {
        assertEquals(Tri.UNKNOWN, eval("occupation" to Answer.Multi(listOf("freelance"))).statusOf("kokuho"))
        // 会社員を兼ねると 0.6 - 1.0 で出ない。確定申告は必須のまま
        val both = eval("occupation" to Answer.Multi(listOf("employee", "freelance")))
        assertNull(both.find("kokuho"))
        assertEquals(Tri.TRUE, both.statusOf("kakutei"))
    }

    @Test
    fun itemsAreSortedByScore() {
        assertEquals(listOf("kokuho", "nenkin"), eval(resigned, unemployed).likely.map { it.procedure.id })
    }

    @Test
    fun ageIsComputedFromBirthDate() {
        // 2026-10-17 時点: 当日生まれは20歳、翌日生まれはまだ19歳
        assertEquals(Tri.TRUE, eval(resigned, unemployed, "birth_date" to Answer.Date("2006-10-17")).statusOf("nenkin"))
        assertNull(eval(resigned, unemployed, "birth_date" to Answer.Date("2006-10-18")).find("nenkin"))
    }

    @Test
    fun ageAtYearEndIsForTaxRules() {
        // 1961-12-01 生まれは、2026-10-17 時点で64歳だが、年末には65歳 → 年末時点の年齢で判定する控除の対象
        assertEquals(Tri.TRUE, eval("birth_date" to Answer.Date("1961-12-01")).statusOf("nenkin_kojo"))
        assertEquals(Tri.FALSE, eval("birth_date" to Answer.Date("1962-01-01")).statusOf("nenkin_kojo"))
    }

    @Test
    fun numberAnswerIsComparedAsIs() {
        val employee = "occupation" to Answer.Multi(listOf("employee"))
        assertEquals(Tri.TRUE, eval(employee, "annual_income" to Answer.Number(25_000_000)).statusOf("kakutei"))
        assertNull(eval(employee, "annual_income" to Answer.Number(20_000_000)).find("kakutei"))
    }

    @Test
    fun rangeAnswerDecidesOnlyWhenWholeRangeAgrees() {
        val employee = "occupation" to Answer.Multi(listOf("employee"))
        assertEquals(Tri.TRUE, eval(employee, resigned, "annual_income" to Answer.Range(20_000_001, null)).statusOf("kakutei"))
        // 2,000万円以下なら会社員の行は数えず、退職の 0.4 だけ → 出ない
        assertNull(eval(employee, resigned, "annual_income" to Answer.Range(0, 20_000_000)).find("kakutei"))
    }
}
