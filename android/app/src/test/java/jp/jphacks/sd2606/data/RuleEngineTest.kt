package jp.jphacks.sd2606.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEngineTest {
    // backend/data/procedures の書き方と同じ形の、小さな制度データ
    private val catalog = Json.decodeFromString<Catalog>(
        """
        {
          "procedures": [
            {"id": "nenkin", "title": "国民年金", "category": "pension", "summary": "", "priority": 90,
             "match": [{"reason": "退職して20〜59歳", "when": [
               {"field": "life_events", "op": "contains", "value": "resign"},
               {"field": "age", "op": "gte", "value": 20},
               {"field": "age", "op": "lt", "value": 60}]}]},
            {"id": "nenkin_kojo", "title": "公的年金等控除", "category": "tax", "summary": "", "priority": 50,
             "match": [{"reason": "年末時点で65歳以上", "when": [{"field": "age_at_year_end", "op": "gte", "value": 65}]}]},
            {"id": "kokuho", "title": "国保", "category": "insurance", "summary": "", "priority": 80,
             "match": [{"reason": "フリーランスで会社員ではない", "when": [
               {"field": "occupation", "op": "in", "value": ["freelance"]},
               {"field": "occupation", "op": "ne", "value": "employee"}]}]},
            {"id": "kakutei", "title": "確定申告", "category": "tax", "summary": "", "priority": 70,
             "match": [
               {"reason": "フリーランス", "when": [{"field": "occupation", "op": "in", "value": ["freelance"]}]},
               {"reason": "高収入の会社員", "when": [
                 {"field": "occupation", "op": "eq", "value": "employee"},
                 {"field": "annual_income", "op": "gt", "value": 20000000}]}]}
          ],
          "questions": [
            {"field": "birth_date", "kind": "date", "text": "生年月日は？", "provides": ["age", "age_at_year_end"]},
            {"field": "annual_income", "kind": "range", "text": "年収は？", "choices": [
              {"label": "2,000万円以下", "min": 0, "max": 20000000}, {"label": "2,000万円超", "min": 20000001}]}
          ]
        }
        """,
    )

    private val today = LocalDate.of(2026, 10, 17)

    private fun eval(vararg answers: Pair<String, Answer>) = RuleEngine.evaluate(catalog.procedures, answers.toMap(), today)

    private val resigned = "life_events" to Answer.Multi(listOf("resign"))

    @Test
    fun unansweredFieldMakesProcedurePending() {
        val e = eval(resigned, "occupation" to Answer.Single("unemployed"))
        assertEquals(Tri.UNKNOWN, e.statusOf("nenkin"))
        assertEquals(listOf("age"), e.pending.single { it.procedure.id == "nenkin" }.missing)
        assertEquals(Tri.FALSE, e.statusOf("kakutei"))
    }

    @Test
    fun rangeAnswerDecidesWhenWholeRangeAgrees() {
        assertEquals(Tri.TRUE, eval(resigned, "age" to Answer.Range(20, 59)).statusOf("nenkin"))
        assertEquals(Tri.FALSE, eval(resigned, "age" to Answer.Range(60, null)).statusOf("nenkin"))
        assertEquals(Tri.FALSE, eval(resigned, "age" to Answer.Range(0, 19)).statusOf("nenkin"))
        // 範囲が境目をまたぐと決められない
        assertEquals(Tri.UNKNOWN, eval(resigned, "age" to Answer.Range(50, 69)).statusOf("nenkin"))
    }

    @Test
    fun falseConditionSkipsQuestionsForThatClause() {
        // 退職していなければ、年齢を聞くまでもなく対象外
        val e = eval("life_events" to Answer.Multi(emptyList()))
        assertEquals(Tri.FALSE, e.statusOf("nenkin"))
    }

    @Test
    fun anyTrueClauseMatchesEvenIfOthersAreUnknown() {
        val e = eval("occupation" to Answer.Single("freelance"))
        assertEquals(Tri.TRUE, e.statusOf("kakutei"))
        assertEquals(listOf("フリーランス"), e.matched.single { it.procedure.id == "kakutei" }.reasons)
    }

    @Test
    fun ageIsComputedFromBirthDate() {
        // 2026-10-17 時点: 前日生まれは20歳、翌日生まれはまだ19歳
        assertEquals(Tri.TRUE, eval(resigned, "birth_date" to Answer.Date("2006-10-16")).statusOf("nenkin"))
        assertEquals(Tri.TRUE, eval(resigned, "birth_date" to Answer.Date("2006-10-17")).statusOf("nenkin"))
        assertEquals(Tri.FALSE, eval(resigned, "birth_date" to Answer.Date("2006-10-18")).statusOf("nenkin"))
    }

    @Test
    fun ageAtYearEndIsForTaxRules() {
        // 1961-12-01 生まれは、2026-10-17 時点で64歳だが、年末には65歳 → 年末時点の年齢で判定する控除の対象
        val e = eval("birth_date" to Answer.Date("1961-12-01"))
        assertEquals(Tri.FALSE, e.statusOf("nenkin"))
        assertEquals(Tri.TRUE, e.statusOf("nenkin_kojo"))
        assertEquals(Tri.FALSE, eval("birth_date" to Answer.Date("1962-01-01")).statusOf("nenkin_kojo"))
    }

    @Test
    fun missingAgeAsksForBirthDate() {
        val pq = RuleEngine.pendingQuestions(eval(resigned), catalog.questions)
        assertEquals("birth_date", pq.first().question.field)
        assertEquals(listOf("nenkin", "nenkin_kojo"), pq.first().procedures.map { it.id })
    }

    @Test
    fun multipleOccupationsMatchAnyAndExcludeWithNe() {
        assertEquals(Tri.TRUE, eval("occupation" to Answer.Multi(listOf("freelance"))).statusOf("kokuho"))
        // 会社員を兼ねるフリーランスは国保の対象外、確定申告は対象
        val both = eval("occupation" to Answer.Multi(listOf("employee", "freelance")))
        assertEquals(Tri.FALSE, both.statusOf("kokuho"))
        assertEquals(Tri.TRUE, both.statusOf("kakutei"))
    }

    @Test
    fun incomeIsAskedOnlyForEmployees() {
        val employee = eval("occupation" to Answer.Single("employee"))
        val questions = RuleEngine.pendingQuestions(employee, catalog.questions).map { it.question.field }
        assertEquals(listOf("birth_date", "annual_income"), questions)
        assertEquals(Tri.FALSE, eval("occupation" to Answer.Single("employee"), "annual_income" to Answer.Range(0, 20_000_000)).statusOf("kakutei"))
        assertEquals(Tri.TRUE, eval("occupation" to Answer.Single("employee"), "annual_income" to Answer.Range(20_000_001, null)).statusOf("kakutei"))
    }

    @Test
    fun pendingQuestionsAreOrderedByPriority() {
        val e = eval()
        val pq = RuleEngine.pendingQuestions(e, catalog.questions)
        assertEquals("birth_date", pq.first().question.field) // 国民年金（優先度90）に関わる質問が先
    }
}
