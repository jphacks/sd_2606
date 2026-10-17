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
          "threshold": null,
          "procedures": [
            {"id": "nenkin", "title": "国民年金", "category": "pension", "summary": "", "due": {"days": 14}, "relations": [
              {"field": "age_at_year_end", "value": 20, "flag": "conditional", "score": 1.0, "reason": "今年20歳",
               "when": [{"field": "occupation", "op": "ne", "value": "employee"}]},
              {"field": "occupation", "op": "in", "value": ["unemployed"], "flag": "conditional", "score": 0.8, "reason": "無職の20〜59歳",
               "when": [{"field": "age", "op": "gte", "value": 20}, {"field": "age", "op": "lt", "value": 60},
                        {"field": "occupation", "op": "ne", "value": "employee"}]},
              {"field": "occupation", "value": "employee", "flag": "unrelated", "score": -1.0, "reason": "厚生年金"},
              {"field": "age_at_year_end", "op": "lt", "value": 20, "flag": "unrelated", "score": -1.0, "reason": "20歳から"}],
             "steps": [
              {"title": "20歳", "detail": "", "when": [{"field": "age_at_year_end", "op": "lte", "value": 20}]},
              {"title": "共通", "detail": "", "documents": ["マイナンバーカード"]},
              {"title": "無職", "detail": "", "documents": ["離職票", "マイナンバーカード"],
               "when": [{"field": "occupation", "value": "unemployed"}]}]},
            {"id": "kokuho", "title": "国保", "category": "insurance", "summary": "", "relations": [
              {"field": "occupation", "op": "in", "value": ["freelance"], "flag": "conditional", "score": 0.8, "reason": "フリーランス"},
              {"field": "occupation", "value": "unemployed", "flag": "conditional", "score": 0.7, "reason": "無職（3択）"},
              {"field": "occupation", "value": "employee", "flag": "unrelated", "score": -1.0, "reason": "会社員"},
              {"field": "health_insurance", "op": "in", "value": ["national", "dependent"], "flag": "unrelated", "score": -1.0, "reason": "加入済み"}]},
            {"id": "kakutei", "title": "確定申告", "category": "tax", "summary": "", "due": {"month": 3, "day": 15}, "relations": [
              {"field": "occupation", "op": "in", "value": ["freelance"], "flag": "required", "score": 0.9, "reason": "フリーランス"},
              {"field": "occupation", "value": "employee", "flag": "conditional", "score": 0.3, "reason": "高収入の会社員",
               "when": [{"field": "annual_income", "op": "gt", "value": 20000000}]},
              {"field": "occupation", "value": "employee", "flag": "conditional", "score": -0.3}]},
            {"id": "kojo", "title": "扶養控除", "category": "tax", "summary": "", "due": {"month": 11, "day": 30}, "relations": [
              {"field": "dependents", "op": "gt", "value": 0, "flag": "required", "score": 0.8, "reason": "扶養している家族がいる"},
              {"field": "dependents", "value": 0, "flag": "unrelated", "score": -1.0, "reason": "扶養している家族がいない"}]},
            {"id": "nenkin_kojo", "title": "公的年金等控除", "category": "tax", "summary": "", "relations": [
              {"field": "age_at_year_end", "op": "gte", "value": 65, "flag": "required", "score": 0.5, "reason": "年末時点で65歳以上"}]},
            {"id": "hazureru", "title": "扶養から外れる", "category": "insurance", "summary": "", "relations": [
              {"field": "annual_income", "op": "gte", "value": 1500000, "flag": "required", "score": 0.6, "reason": "150万円以上"},
              {"field": "annual_income", "op": "lte", "value": 1360000, "flag": "unrelated", "score": -1.0, "reason": "19〜22歳で136万円以下",
               "when": [{"field": "age_at_year_end", "op": "in", "value": [19, 20, 21, 22]}]}]}
          ],
          "questions": [
            {"field": "occupation", "kind": "multi", "text": "働き方は？", "label": "働き方", "required": true, "choices": [
              {"label": "会社員", "value": "employee"}, {"label": "フリーランス", "value": "freelance"}, {"label": "無職", "value": "unemployed"}]},
            {"field": "birth_date", "kind": "date", "text": "生年月日は？", "label": "生年月日", "required": true, "provides": ["age", "age_at_year_end"]},
            {"field": "annual_income", "kind": "range", "text": "年収は？", "label": "年収", "choices": [
              {"label": "2,000万円以下", "min": 0, "max": 20000000}, {"label": "2,000万円超", "min": 20000001}]},
            {"field": "dependents", "kind": "number", "text": "扶養している家族は？", "label": "扶養している家族の人数", "unit": "人"},
            {"field": "income", "kind": "number", "text": "年収は？", "label": "年収", "unit": "万円", "scale": 10000},
            {"field": "health_insurance", "kind": "single", "text": "健康保険は？", "label": "健康保険", "choices": [
              {"label": "勤務先", "value": "employer"}, {"label": "国保", "value": "national"}]}
          ]
        }
        """,
    )

    private val today = LocalDate.of(2026, 10, 17)

    private fun eval(vararg answers: Pair<String, Answer>, threshold: Double? = catalog.threshold) =
        RuleEngine.evaluate(catalog.procedures, answers.toMap(), threshold, today)

    private fun occupation(vararg values: String) = "occupation" to Answer.Multi(values.toList())
    private fun born(date: String) = "birth_date" to Answer.Date(date)

    private val unemployed = occupation("unemployed")
    private val employee = occupation("employee")

    @Test
    fun requiredRowMakesProcedureRequired() {
        assertEquals(Status.REQUIRED, eval(occupation("freelance")).statusOf("kakutei"))
    }

    @Test
    fun conditionalWithUnknownConditionIsLikelyAndAsksForBirthDate() {
        val nenkin = eval(unemployed).find("nenkin")!!
        assertEquals(Status.LIKELY, nenkin.status)
        assertEquals(0.8, nenkin.score, 1e-9)
        assertEquals(listOf("age_at_year_end", "age"), nenkin.missing)
        assertEquals("birth_date", RuleEngine.questionFor("age", catalog.questions)?.field)
    }

    @Test
    fun conditionalBecomesRequiredWhenAllConditionsHold() {
        assertEquals(Status.REQUIRED, eval(unemployed, born("1998-04-01")).statusOf("nenkin"))
    }

    @Test
    fun turningTwentyThisYearIsRequiredBeforeTheBirthday() {
        // 2006-12-01 生まれは、2026-10-17 時点で19歳だが、年末には20歳
        assertEquals(Status.REQUIRED, eval(occupation("student"), born("2006-12-01")).statusOf("nenkin"))
    }

    @Test
    fun unrelatedRowExcludesProcedureWithReason() {
        val nenkin = eval(employee).find("nenkin")!!
        assertEquals(Status.UNRELATED, nenkin.status)
        assertEquals(listOf("厚生年金"), nenkin.reasons)
    }

    @Test
    fun requiredWinsOverUnrelated() {
        val both = eval(occupation("employee", "freelance"))
        assertEquals(Status.REQUIRED, both.statusOf("kakutei"))
        // 国保は会社員の行で対象外（フリーランスの点数があっても）
        assertEquals(Status.UNRELATED, both.statusOf("kokuho"))
    }

    @Test
    fun zeroScoreProcedureIsListedBecauseThresholdIsMinusInfinity() {
        val kokuho = eval().find("kokuho")!!
        assertEquals(Status.LIKELY, kokuho.status)
        assertEquals(0.0, kokuho.score, 1e-9)
        // 答えると対象外になりうる項目を聞く
        assertEquals(listOf("occupation", "health_insurance"), kokuho.missing)
    }

    @Test
    fun thresholdStillWorksWhenSet() {
        assertEquals(Status.UNRELATED, eval(threshold = 0.5).statusOf("kokuho"))
        assertEquals(Status.LIKELY, eval(unemployed, threshold = 0.5).statusOf("kokuho"))
    }

    @Test
    fun unknownTriggerOfRequiredRowAsksForTheNumber() {
        val kojo = eval(unemployed).find("kojo")!!
        assertEquals(Status.LIKELY, kojo.status)
        assertEquals(listOf("dependents"), kojo.missing)
        assertEquals(Status.REQUIRED, eval("dependents" to Answer.Number(2)).statusOf("kojo"))
        assertEquals(Status.UNRELATED, eval("dependents" to Answer.Number(0)).statusOf("kojo"))
    }

    @Test
    fun sortByRelevanceAndByDeadline() {
        val items = eval(unemployed).items
        assertEquals(listOf("nenkin", "kokuho"), RuleEngine.sort(items, SortOrder.RELEVANCE, today).take(2).map { it.procedure.id })
        // 期限: 国民年金 14日後（10/31）→ 扶養控除 11/30 → 確定申告 翌年3/15 → 期限なしは点数の高い順
        assertEquals(
            listOf("nenkin", "kojo", "kakutei", "kokuho", "nenkin_kojo", "hazureru"),
            RuleEngine.sort(items, SortOrder.DEADLINE, today).map { it.procedure.id },
        )
    }

    @Test
    fun dueDateIsTheNextOccurrence() {
        assertEquals(LocalDate.of(2026, 10, 22), Due(days = 5).dateFrom(today))
        assertEquals(LocalDate.of(2026, 11, 30), Due(month = 11, day = 30).dateFrom(today))
        assertEquals(LocalDate.of(2027, 3, 15), Due(month = 3, day = 15).dateFrom(today))
        assertEquals(LocalDate.of(2027, 6, 30), Due(month = 6).dateFrom(today)) // day が無ければ月末
        assertEquals(LocalDate.of(2027, 2, 28), Due(month = 2, day = 29).dateFrom(today))
        assertEquals(today, Due(month = 10, day = 17).dateFrom(today))
    }

    @Test
    fun stepsThatDoNotApplyAreHidden() {
        // 26歳の学生: 20歳のステップと無職のステップは出さない
        val nenkin = eval(occupation("student"), born("2000-04-01")).find("nenkin")!!
        assertEquals(listOf("共通"), nenkin.steps.map { it.step.title })
        assertEquals(listOf(Document("マイナンバーカード", false)), nenkin.documents)
    }

    @Test
    fun stepsWithUnknownConditionsAreShownAsConditional() {
        val nenkin = eval().find("nenkin")!!
        assertEquals(listOf("20歳", "共通", "無職"), nenkin.steps.map { it.step.title })
        assertEquals(listOf(listOf("age_at_year_end"), emptyList(), listOf("occupation")), nenkin.steps.map { it.unknown })
        // 必要なもの: 共通のステップでも使うマイナンバーカードは確定、無職のステップだけの離職票は「当てはまる場合」
        assertEquals(listOf(Document("マイナンバーカード", false), Document("離職票", true)), nenkin.documents)
    }

    @Test
    fun ageIsComputedFromBirthDate() {
        // 2026-10-17 時点: 当日生まれは20歳
        assertEquals(Status.REQUIRED, eval(unemployed, born("2006-10-17")).statusOf("nenkin"))
        // 年末でも19歳なら対象外
        assertEquals(Status.UNRELATED, eval(unemployed, born("2007-01-01")).statusOf("nenkin"))
    }

    @Test
    fun ageAtYearEndIsForTaxRules() {
        // 1961-12-01 生まれは、2026-10-17 時点で64歳だが、年末には65歳 → 年末時点の年齢で判定する控除の対象
        assertEquals(Status.REQUIRED, eval(born("1961-12-01")).statusOf("nenkin_kojo"))
        assertEquals(Status.LIKELY, eval(born("1962-01-01")).statusOf("nenkin_kojo"))
    }

    @Test
    fun numberAnswerIsComparedAsIs() {
        assertEquals(Status.REQUIRED, eval(employee, "annual_income" to Answer.Number(25_000_000)).statusOf("kakutei"))
        val low = eval(employee, "annual_income" to Answer.Number(20_000_000)).find("kakutei")!!
        assertEquals(Status.LIKELY, low.status)
        assertEquals(-0.3, low.score, 1e-9) // 高収入の行は数えず、会社員の -0.3 だけ
    }

    @Test
    fun rangeAnswerDecidesOnlyWhenWholeRangeAgrees() {
        assertEquals(Status.REQUIRED, eval(employee, "annual_income" to Answer.Range(20_000_001, null)).statusOf("kakutei"))
        assertEquals(Status.LIKELY, eval(employee, "annual_income" to Answer.Range(0, 20_000_000)).statusOf("kakutei"))
        assertEquals(listOf("annual_income"), eval(employee, "annual_income" to Answer.Range(0, null)).find("kakutei")!!.missing)
    }

    @Test
    fun unrelatedRowWithConditionsExcludesOnlyWhenAllHold() {
        val income = "annual_income" to Answer.Number(1_200_000)
        // 年末に20歳で136万円以下 → 対象外（理由つき）
        val young = eval(income, born("2006-05-01")).find("hazureru")!!
        assert(young.status == Status.UNRELATED && young.reasons == listOf("19〜22歳で136万円以下"))
        // 25歳なら条件を満たさず、対象外にならない
        assertEquals(Status.LIKELY, eval(income, born("2001-05-01")).statusOf("hazureru"))
        // 生年月日が分からなければ対象外にせず、生年月日を聞く
        val unknown = eval(income).find("hazureru")!!
        assertEquals(Status.LIKELY, unknown.status)
        assertEquals(listOf("age_at_year_end"), unknown.missing)
    }

    @Test
    fun placeholdersAreFilledWithAnswers() {
        val questions = catalog.questions
        val answers = mapOf("income" to Answer.Number(1_400_000), born("2006-05-01"))
        assertEquals(
            "見込みの140万円なら、年末に20歳",
            RuleEngine.fillIn("見込みの{income}なら、年末に{age_at_year_end}", answers, questions, today),
        )
        assertEquals("見込みの（年収は未入力）", RuleEngine.fillIn("見込みの{income}", emptyMap(), questions, today))
    }
}
