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

    @Test
    fun dismissedProcedureIsUnrelatedRegardlessOfRules() {
        // フリーランスの確定申告はルールでは必要だが、本人が「関係ない」にしたら対象外
        val evaluation = RuleEngine.evaluate(catalog.procedures, mapOf(occupation("freelance")), catalog.threshold, today, dismissed = setOf("kakutei"))
        val kakutei = evaluation.find("kakutei")!!
        assertEquals(Status.UNRELATED, kakutei.status)
        assertEquals(true, kakutei.dismissed)
        assertEquals(listOf(RuleEngine.DISMISSED_REASON), kakutei.reasons)
        assertEquals(emptyList<String>(), kakutei.missing)
        // ほかの手続きには影響しない
        assertEquals(false, evaluation.find("kokuho")!!.dismissed)
    }

    @Test
    fun confirmedProcedureIsRequiredRegardlessOfRules() {
        // 「当てはまる」にしたものは、ルールの判定にかかわらず必要。「関係ない」と両方なら「関係ない」
        val likely = RuleEngine.evaluate(catalog.procedures, mapOf(occupation("freelance")), catalog.threshold, today).likely.first().procedure.id
        val evaluation = RuleEngine.evaluate(catalog.procedures, mapOf(occupation("freelance")), catalog.threshold, today, confirmed = setOf(likely))
        val item = evaluation.find(likely)!!
        assertEquals(Status.REQUIRED, item.status)
        assertEquals(true, item.confirmed)
        // 「当てはまる」にしたことを先頭に、ルールで当てはまった条件も残す
        val before = RuleEngine.evaluate(catalog.procedures, mapOf(occupation("freelance")), catalog.threshold, today).find(likely)!!
        assertEquals(listOf(RuleEngine.CONFIRMED_REASON) + before.reasons, item.reasons)
        assertEquals(emptyList<String>(), item.missing)
        val both = RuleEngine.evaluate(catalog.procedures, mapOf(occupation("freelance")), catalog.threshold, today, dismissed = setOf(likely), confirmed = setOf(likely))
        assertEquals(Status.UNRELATED, both.statusOf(likely))
    }

    @Test
    fun stepDetailsAreParsed() {
        val proc = Json.decodeFromString<ProcedureDef>(
            """{"id": "x", "title": "", "category": "tax", "summary": "",
                "steps": [{"title": "出す", "detail": "1文", "details": ["窓口で書く", "郵送もできる"]}]}""",
        )
        assertEquals(listOf("窓口で書く", "郵送もできる"), proc.steps.single().details)
    }

    @Test
    fun nextDueFollowsMonthEnd() {
        val monthly = Reminder(label = "納付期限（毎月末）", day = 31)
        assertEquals(LocalDate.of(2026, 10, 31), monthly.nextDue(today))
        assertEquals(LocalDate.of(2026, 11, 30), monthly.nextDue(LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2027, 3, 15), Reminder(label = "確定申告", month = 3, day = 15).nextDue(today))
        // 今日が期限の日なら今日
        assertEquals(today, Reminder(label = "x", month = 10, day = 17).nextDue(today))
    }

    @Test
    fun upcomingDeadlinesSkipUnrelatedAndCompleted() {
        val reminder = Reminder(label = "確定申告の期限", month = 3, day = 15)
        val soonReminder = Reminder(label = "保険料（毎月末）", day = 31)
        val procs = catalog.procedures.map {
            when (it.id) {
                "kakutei" -> it.copy(reminders = listOf(reminder))
                "kokuho" -> it.copy(reminders = listOf(soonReminder))
                else -> it
            }
        }
        val freelance = RuleEngine.evaluate(procs, mapOf(occupation("freelance")), catalog.threshold, today)
        // 既定では必要な手続き（確定申告）の期限だけ。関係がありそうな手続き（国保）の期限は includeLikely で出す
        assertEquals(listOf("確定申告の期限"), RuleEngine.upcomingReminders(freelance, emptySet(), today).map { it.reminder.label })
        val upcoming = RuleEngine.upcomingReminders(freelance, emptySet(), today, includeLikely = true)
        // 期限の早い順。10/31 は1週間前（10/24）を過ぎていないので「もうすぐ」ではない
        assertEquals(listOf("保険料（毎月末）", "確定申告の期限"), upcoming.map { it.reminder.label })
        assertEquals(listOf(true, false), upcoming.map { it.likely })
        assertEquals(listOf(false, false), upcoming.map { it.soon })
        assertEquals(true, RuleEngine.upcomingReminders(freelance, emptySet(), LocalDate.of(2026, 10, 25), includeLikely = true).first().soon)
        // 完了した手続きの期限は出さない
        assertEquals(listOf("保険料（毎月末）"), RuleEngine.upcomingReminders(freelance, setOf("kakutei"), today, includeLikely = true).map { it.reminder.label })
        // 「当てはまる」にした手続きの期限は、既定でも出す
        val confirmed = RuleEngine.evaluate(procs, mapOf(occupation("freelance")), catalog.threshold, today, confirmed = setOf("kokuho"))
        assertEquals(listOf("保険料（毎月末）", "確定申告の期限"), RuleEngine.upcomingReminders(confirmed, emptySet(), today).map { it.reminder.label })
        // 対象外（会社員の国保）や「関係ない」にした手続きの期限も出さない
        val employee = RuleEngine.evaluate(procs, mapOf(occupation("employee", "freelance")), catalog.threshold, today, dismissed = setOf("kakutei"))
        assertEquals(emptyList<String>(), RuleEngine.upcomingReminders(employee, emptySet(), today, includeLikely = true).map { it.reminder.label })
    }

    @Test
    fun flowStopsAtUnansweredChoiceAndShowsAmountsAfterIt() {
        val proc = Json.decodeFromString<ProcedureDef>(
            """{"id": "nenkin", "title": "", "category": "pension", "summary": "",
                "amounts": [{"kind": "nenkin", "card": true}],
                "relations": [{"field": "age", "op": "gte", "value": 0, "flag": "conditional", "score": 0.1, "reason": "x"}],
                "reminders": [{"label": "特例の申請", "month": 4, "day": 30, "when": [{"field": "plan", "value": "tokurei"}]}],
                "steps": [
                  {"title": "お知らせを確かめる", "detail": ""},
                  {"choice": {"field": "plan", "title": "保険料をどうするか", "options": [
                    {"value": "tokurei", "label": "特例", "detail": "",
                     "steps": [{"title": "申請する", "detail": "", "documents": ["学生証"]}], "amounts": [{"kind": "nenkin_tokurei"}]},
                    {"value": "pay", "label": "納める", "detail": "",
                     "steps": [{"title": "納める", "detail": ""}], "amounts": [{"kind": "nenkin"}]}]}}]}""",
        )
        // 選ぶまでは分岐で止まり、金額も期限も出さない
        val before = RuleEngine.flow(proc, emptyMap(), today)
        assertEquals(false, before.decided)
        assertEquals(listOf("お知らせを確かめる"), before.steps.map { it.step.title })
        assertEquals(null, (before.items.last() as FlowItem.Choice).selected)
        assertEquals(emptyList<AmountSpec>(), before.amounts)
        assertEquals(emptyList<Reminder>(), RuleEngine.evaluate(listOf(proc), emptyMap(), null, today).items.single().reminders)
        // 選ぶと、その選択肢のステップが続き（番号も続く）、選択肢の金額だけを出す
        val after = RuleEngine.flow(proc, mapOf("plan" to Answer.Single("tokurei")), today)
        assertEquals(true, after.decided)
        assertEquals(listOf(1, 2), after.items.filterIsInstance<FlowItem.Step>().map { it.number })
        assertEquals(listOf("nenkin_tokurei"), after.amounts.map { it.kind })
        assertEquals(listOf("学生証"), RuleEngine.documentsOf(after.steps).map { it.name })
        assertEquals(setOf("plan"), RuleEngine.choiceFields(listOf(proc)))
        val withPlan = RuleEngine.evaluate(listOf(proc), mapOf("plan" to Answer.Single("tokurei")), null, today)
        assertEquals(listOf("特例の申請"), withPlan.items.single().reminders.map { it.label })
    }

    @Test
    fun inlineYesNoQuestionIsAskedOnTheCardAndYesMakesItRequired() {
        // kakutei_shinkoku.yaml と同じ形: 掛け持ち（カードの中で聞く質問）に「はい」なら必要
        val proc = Json.decodeFromString<ProcedureDef>(
            """
            {"id": "kakutei", "title": "確定申告", "category": "tax", "summary": "", "relations": [
              {"field": "occupation", "op": "in", "value": ["part_time", "student"], "flag": "conditional", "score": 0.5, "reason": "掛け持ち",
               "when": [{"field": "multiple_jobs", "value": "yes"}, {"field": "annual_income", "op": "gt", "value": 0}]},
              {"field": "multiple_jobs", "value": "no", "flag": "conditional", "score": -0.2}]}
            """,
        )
        fun judge(vararg answers: Pair<String, Answer>) = RuleEngine.evaluate(listOf(proc), answers.toMap(), null, today).find("kakutei")!!
        val base = arrayOf(occupation("student", "part_time"), "annual_income" to Answer.Number(1_300_000))
        val unanswered = judge(*base)
        assertEquals(Status.LIKELY, unanswered.status)
        assertEquals(listOf("multiple_jobs"), unanswered.missing)
        val yes = judge(*base, "multiple_jobs" to Answer.Single("yes"))
        assertEquals(Status.REQUIRED, yes.status)
        assertEquals(listOf("掛け持ち"), yes.reasons)
        val no = judge(*base, "multiple_jobs" to Answer.Single("no"))
        assertEquals(Status.LIKELY, no.status)
        assertEquals(emptyList<String>(), no.missing)
        // 収入が無ければ聞かない
        assertEquals(emptyList<String>(), judge(occupation("student"), "annual_income" to Answer.Number(0)).missing)
    }

    @Test
    fun answeredQuestionsListOptionalAnswersAndChosenBranchesOfTheProcedure() {
        // 国保は health_insurance を判定に使う。必須の働き方は最初の画面で変えるので出さない
        val kokuho = catalog.procedures.first { it.id == "kokuho" }
        val answers = mapOf(occupation("freelance"), "health_insurance" to Answer.Single("employer"), "dependents" to Answer.Number(1))
        assertEquals(listOf("health_insurance"), RuleEngine.answeredQuestions(kokuho, catalog.questions, answers, today).map { it.field })
        // 答えていなければ出さない
        assertEquals(emptyList<String>(), RuleEngine.answeredQuestions(kokuho, catalog.questions, mapOf(occupation("freelance")), today).map { it.field })
        // 分岐で選んだものは、その選択肢を質問の形にして出す
        val withChoice = Json.decodeFromString<ProcedureDef>(
            """
            {"id": "nenkin", "title": "国民年金", "category": "pension", "summary": "", "relations": [
              {"field": "age_at_year_end", "op": "gte", "value": 20, "flag": "required", "score": 1.0, "reason": "20歳"}],
             "steps": [{"choice": {"field": "nenkin_pay", "title": "保険料をどうするか", "options": [
               {"value": "tokurei", "label": "学生納付特例を使う", "detail": ""}, {"value": "pay", "label": "保険料を納める", "detail": ""}]}}]}
            """,
        )
        val chosen = RuleEngine.answeredQuestions(withChoice, catalog.questions, mapOf("nenkin_pay" to Answer.Single("pay")), today)
        assertEquals(listOf("nenkin_pay"), chosen.map { it.field })
        assertEquals("保険料を納める", chosen.single().describe(Answer.Single("pay")))
        assertEquals(emptyList<String>(), RuleEngine.answeredQuestions(withChoice, catalog.questions, emptyMap(), today).map { it.field })
    }

    @Test
    fun estimateIncomeIsJudgedWithin50PercentAndQuestionsComeOneByOne() {
        // kakutei_shinkoku.yaml と同じ形: 178万円超で掛け持ち、掛け持ち先の給与が20万円超なら必要
        val proc = Json.decodeFromString<ProcedureDef>(
            """
            {"id": "kakutei", "title": "確定申告をする", "category": "tax", "summary": "", "relations": [
              {"field": "part_time", "value": "yes", "flag": "conditional", "score": 0.5, "reason": "掛け持ち",
               "when": [{"field": "annual_income", "op": "gt", "value": 1780000}, {"field": "multiple_jobs", "value": "yes"},
                        {"field": "side_job_over_200k", "value": "yes"}]}]}
            """,
        )
        fun judge(vararg answers: Pair<String, Answer>) = RuleEngine.evaluate(listOf(proc), answers.toMap(), null, today).find("kakutei")!!
        val partTime = "part_time" to Answer.Single("yes")
        fun income(v: Long, basis: String) = arrayOf("annual_income" to Answer.Number(v), "income_basis" to Answer.Single(basis))
        // 確定で130万円: 178万円以下と分かるので、掛け持ちは聞かない
        judge(partTime, *income(1_300_000, "actual")).let {
            assertEquals(Status.LIKELY, it.status)
            assertEquals(emptyList<String>(), it.missing)
            assertEquals(emptyList<String>(), it.maybeRequiredBy)
        }
        // 確定で200万円: 掛け持ちを聞き、「はい」なら次に掛け持ち先の給与を聞く。両方「はい」で必要
        assertEquals(listOf("multiple_jobs"), judge(partTime, *income(2_000_000, "actual")).missing)
        val yes = "multiple_jobs" to Answer.Single("yes")
        assertEquals(listOf("side_job_over_200k"), judge(partTime, *income(2_000_000, "actual"), yes).missing)
        assertEquals(Status.REQUIRED, judge(partTime, *income(2_000_000, "actual"), yes, "side_job_over_200k" to Answer.Single("yes")).status)
        // 見込みで130万円（65万〜195万円）: 178万円をまたぐので「必要な場合があります」。年収は聞き直さず、掛け持ちは聞く
        judge(partTime, *income(1_300_000, "estimate"), yes, "side_job_over_200k" to Answer.Single("yes")).let {
            assertEquals(Status.LIKELY, it.status)
            assertEquals(listOf("annual_income"), it.maybeRequiredBy)
            assertEquals(emptyList<String>(), it.missing)
        }
        assertEquals(listOf("multiple_jobs"), judge(partTime, *income(1_300_000, "estimate")).missing)
        // 見込みで100万円（50万〜150万円）: 178万円に届かないので聞かない
        assertEquals(emptyList<String>(), judge(partTime, *income(1_000_000, "estimate")).missing)
        assertEquals("65万〜195万円", RuleEngine.describeRange(RuleEngine.estimateRange(mapOf(*income(1_300_000, "estimate")))!!))
    }
}
