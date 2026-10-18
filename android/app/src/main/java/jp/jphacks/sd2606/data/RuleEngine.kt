package jp.jphacks.sd2606.data

import java.time.LocalDate
import java.time.Period
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 条件の判定結果。未回答の項目があると UNKNOWN になる。 */
enum class Tri { TRUE, FALSE, UNKNOWN }

/** 手続きの判定: 必要（必須）/ 関係がありそう / 対象外。 */
enum class Status { REQUIRED, LIKELY, UNRELATED }

/** 一覧の並べ方。 */
enum class SortOrder { RELEVANCE, DEADLINE }

/**
 * 手続きのステップのうち、この人に表示するもの。
 * unknown は、まだ答えていないため当てはまるか分からない条件の項目（空なら当てはまる）。
 */
data class StepView(val step: FlowStep, val unknown: List<String>)

/**
 * これからの期限1つ（通知と履歴の時系列に並べる）。soon は「期限の before_days 日前を過ぎた」（もうすぐ）。
 * likely は、必要な手続きではなく関係がありそうな手続きの期限（薄く表示する）。
 */
data class ReminderView(
    val id: String,
    val procedure: ProcedureDef,
    val reminder: Reminder,
    val due: LocalDate,
    val soon: Boolean,
    val likely: Boolean = false,
)

/**
 * 手続きの画面に並べるフローの1行。
 * - Step: 番号つきのステップ
 * - Choice: 分岐。selected が null ならまだ選んでいない（その先は出さない）
 */
sealed interface FlowItem {
    data class Step(val view: StepView, val number: Int) : FlowItem
    data class Choice(val choice: FlowChoice, val selected: FlowOption?) : FlowItem
}

/** 手続きのフロー。decided は分岐をすべて選び終えた（金額を出す）。amounts は出すお金の目安。 */
data class Flow(val items: List<FlowItem>, val decided: Boolean, val amounts: List<AmountSpec>) {
    val steps: List<StepView> get() = items.filterIsInstance<FlowItem.Step>().map { it.view }
}

/** 必要なもの1つ。conditional なら、当てはまるか分からないステップでだけ使う。 */
data class Document(val name: String, val conditional: Boolean)

/**
 * 判定した手続き。
 * - reasons: 必要・関係がありそうなら当てはまった理由（点数の高い順）、対象外なら対象外の理由
 * - missing: 答えると判定が変わりうる項目（「判定には生年月日が必要です」の案内に使う）
 * - steps: 当てはまらない説明を除いたステップ
 * - dismissed: 本人が「自分には関係ない」にした（ルールの判定にかかわらず対象外）
 * - confirmed: 本人が「当てはまる」にした（ルールの判定にかかわらず必要）
 */
data class Scored(
    val procedure: ProcedureDef,
    val status: Status,
    val score: Double,
    val reasons: List<String>,
    val missing: List<String>,
    val steps: List<StepView>,
    val dismissed: Boolean = false,
    val confirmed: Boolean = false,
    /** 当てはまらない条件を除いた、期限の前の通知 */
    val reminders: List<Reminder> = emptyList(),
) {
    val required: Boolean get() = status == Status.REQUIRED

    /** 表示するステップの必要なものをまとめた一覧（同じものは1つにする）。 */
    val documents: List<Document> get() = RuleEngine.documentsOf(steps)
}

data class Evaluation(val items: List<Scored>) {
    val required: List<Scored> = items.filter { it.status == Status.REQUIRED }
    val likely: List<Scored> = items.filter { it.status == Status.LIKELY }
    val unrelated: List<Scored> = items.filter { it.status == Status.UNRELATED }

    fun find(id: String): Scored? = items.firstOrNull { it.procedure.id == id }

    fun statusOf(id: String): Status? = find(id)?.status
}

/**
 * 端末内でのルール判定（backend/app/rules/engine.py と同じ条件の意味）。
 *
 * 未回答の項目は「まだ分からない」として3値で判定し、判定を変えうる項目だけを後から聞く。
 * これにより、最初は最小限の質問（働き方・生年月日）だけで始め、必要になった情報だけを後から聞ける。
 */
object RuleEngine {
    /** 生年月日から計算する項目（questions.yaml の birth_date の provides と対応）。 */
    private val fromBirthDate: Map<String, (LocalDate, LocalDate) -> Int> = mapOf(
        "age" to { birth, today -> ageOn(birth, today) },
        // 所得税の控除（扶養・年金など）はその年の12月31日時点の年齢で決まる
        "age_at_year_end" to { birth, today -> ageOn(birth, LocalDate.of(today.year, 12, 31)) },
    )

    /** on の日の満年齢（誕生日に1つ増える）。 */
    fun ageOn(birth: LocalDate, on: LocalDate): Int = Period.between(birth, on).years

    /** 生年月日の回答から age などを計算して加える。保存するのは生年月日だけで、年齢は判定のたびに計算する。 */
    fun withDerived(answers: Map<String, Answer>, today: LocalDate): Map<String, Answer> {
        val birth = (answers["birth_date"] as? Answer.Date)?.toLocalDate() ?: return answers
        val derived = fromBirthDate.mapValues { (_, f) -> f(birth, today).toLong().let { Answer.Range(it, it) } }
        return derived + answers
    }

    /**
     * 手続きごとに relations を見て、必要・関係がありそう・対象外に分ける。対象外も含めて全件を返す（点数の高い順）。
     *
     * - きっかけに当てはまる行だけを数える
     * - required の行があれば必要。conditional は conditions をすべて満たせば必要、満たさない条件があればその行は数えない、
     *   未回答の条件があれば点数だけ数える。conditions の無い conditional は点数だけ
     * - 必要でなく、unrelated の行が当てはまる（conditions があればすべて満たす）か、点数の合計がしきい値（null は -inf）未満なら対象外
     * - それ以外は関係がありそう
     */
    fun evaluate(
        procedures: List<ProcedureDef>,
        rawAnswers: Map<String, Answer>,
        threshold: Double?,
        today: LocalDate = LocalDate.now(),
        dismissed: Set<String> = emptySet(),
        confirmed: Set<String> = emptySet(),
    ): Evaluation {
        val answers = withDerived(rawAnswers, today)
        val lower = threshold ?: Double.NEGATIVE_INFINITY
        val items = procedures.map { proc ->
            val judged = judge(proc, answers, lower)
            // 本人が「関係ない」「当てはまる」にしたものは、ルールの判定より優先する（両方あれば「関係ない」）。
            // 「当てはまる」では、ルールで当てはまった条件も理由に残す（対象外の理由は残さない）
            when (proc.id) {
                in dismissed -> judged.copy(status = Status.UNRELATED, reasons = listOf(DISMISSED_REASON), missing = emptyList(), dismissed = true)
                in confirmed -> judged.copy(
                    status = Status.REQUIRED,
                    reasons = listOf(CONFIRMED_REASON) + if (judged.status == Status.UNRELATED) emptyList() else judged.reasons,
                    missing = emptyList(),
                    confirmed = true,
                )
                else -> judged
            }
        }
        return Evaluation(items.sortedByDescending { it.score })
    }

    const val DISMISSED_REASON = "自分で「関係ない」にした"
    const val CONFIRMED_REASON = "自分で「当てはまる」にした"

    private fun judge(proc: ProcedureDef, answers: Map<String, Answer>, lower: Double): Scored {
        var isRequired = false
        val excluded = mutableListOf<Relation>()
        val counted = mutableListOf<Relation>()
        val canRequire = linkedSetOf<String>() // 答えると必要になりうる項目
        val canExclude = linkedSetOf<String>() // 答えると対象外になりうる項目
        for (rel in proc.relations) {
            val conditions = rel.conditions.map { it to check(it, answers[it.field]) }
            when (check(rel.trigger(), answers[rel.field])) {
                Tri.FALSE -> continue
                Tri.UNKNOWN -> {
                    when {
                        conditions.any { it.second == Tri.FALSE } -> Unit // 答えても、この行は当てはまらない
                        rel.flag == "unrelated" -> canExclude += rel.field
                        rel.flag == "required" -> canRequire += rel.field
                        conditions.isNotEmpty() -> canRequire += rel.field
                    }
                    continue
                }
                Tri.TRUE -> Unit
            }
            when (rel.flag) {
                "required" -> isRequired = true
                "unrelated" -> {
                    if (conditions.any { it.second == Tri.FALSE }) continue
                    val unknown = conditions.filter { it.second == Tri.UNKNOWN }.map { it.first.field }
                    if (unknown.isNotEmpty()) {
                        canExclude += unknown // 分かるまでは対象外にせず、点数にも数えない
                        continue
                    }
                    excluded += rel
                }
                "conditional" -> {
                    if (conditions.any { it.second == Tri.FALSE }) continue
                    val unknown = conditions.filter { it.second == Tri.UNKNOWN }.map { it.first.field }
                    if (conditions.isNotEmpty() && unknown.isEmpty()) isRequired = true else canRequire += unknown
                }
            }
            counted += rel
        }
        val score = counted.sumOf { it.score }
        val status = when {
            isRequired -> Status.REQUIRED
            excluded.isNotEmpty() || score < lower - 1e-9 -> Status.UNRELATED
            else -> Status.LIKELY
        }
        val reasons = when (status) {
            Status.UNRELATED -> excluded.mapNotNull { it.reason }
            else -> counted.filter { it.score > 0 }.sortedByDescending { it.score }.mapNotNull { it.reason }
        }.distinct()
        val missing = when (status) {
            Status.REQUIRED -> emptyList()
            Status.UNRELATED -> canRequire.toList() // 対象外でも、答えると必要になることがある（必要が優先）
            Status.LIKELY -> (canRequire + canExclude).toList()
        }
        // 分岐で選ぶ値（nenkin_plan など）の条件は、選ぶまで当てはまらないものとして扱う
        val choices = choiceFields(listOf(proc))
        val reminders = proc.reminders.filter { r ->
            r.conditions.all { c -> check(c, answers[c.field]).let { it == Tri.TRUE || (it == Tri.UNKNOWN && c.field !in choices) } }
        }
        return Scored(proc, status, score, reasons, missing, visibleSteps(proc, answers), reminders = reminders)
    }

    fun reminderId(procedure: ProcedureDef, reminder: Reminder): String = "${procedure.id}:${reminder.label}"

    /**
     * これからの1年以内の期限を、手続きの期限ごとに次の1回だけ、早い順に並べる（通知と履歴の時系列）。
     * 必要な手続きの期限だけを出し、includeLikely なら関係がありそうな手続きの期限も出す。
     * 対象外の手続きと、完了した手続き（completed）の期限は出さない。
     */
    fun upcomingReminders(
        evaluation: Evaluation,
        completed: Set<String> = emptySet(),
        today: LocalDate = LocalDate.now(),
        includeLikely: Boolean = false,
    ): List<ReminderView> {
        val shown = if (includeLikely) setOf(Status.REQUIRED, Status.LIKELY) else setOf(Status.REQUIRED)
        val until = today.plusYears(1)
        return evaluation.items.filter { it.status in shown && it.procedure.id !in completed }.flatMap { item ->
            item.reminders.map { r ->
                val due = r.nextDue(today)
                val soon = !today.isBefore(due.minusDays(r.beforeDays.toLong()))
                ReminderView(reminderId(item.procedure, r), item.procedure, r, due, soon, likely = item.status == Status.LIKELY)
            }
        }.filter { it.due.isBefore(until) }.sortedBy { it.due }
    }

    /** 必要なものを、表示するステップの分だけまとめる（同じものは1つにする）。 */
    fun documentsOf(steps: List<StepView>): List<Document> =
        steps.flatMap { s -> s.step.documents.map { Document(it, s.unknown.isNotEmpty()) } }
            .groupBy { it.name }
            .map { (name, docs) -> Document(name, docs.all { it.conditional }) }

    /** 手続きの分岐で選ぶ値の項目（回答と一緒に端末に保存する）。 */
    fun choiceFields(procedures: List<ProcedureDef>): Set<String> =
        procedures.flatMap { allChoices(it.steps) }.map { it.field }.toSet()

    /** ステップの中の分岐（選択肢の先の分岐も含む）。 */
    fun allChoices(steps: List<FlowStep>): List<FlowChoice> =
        steps.flatMap { s -> listOfNotNull(s.choice) + s.choice?.options.orEmpty().flatMap { allChoices(it.steps) } }

    /**
     * 手続きの画面に並べるフロー。当てはまるステップに番号を付けて並べ、分岐では選んだ選択肢のステップを続ける。
     * まだ選んでいない分岐があれば、そこで止める（decided = false。金額も出さない）。
     * 金額は、分岐のある手続きなら選んだ選択肢の amounts、無ければ手続きの amounts。
     */
    fun flow(proc: ProcedureDef, rawAnswers: Map<String, Answer>, today: LocalDate = LocalDate.now()): Flow {
        val answers = withDerived(rawAnswers, today)
        val items = mutableListOf<FlowItem>()
        val chosen = mutableListOf<FlowOption>()
        var hasChoice = false
        var number = 0
        fun walk(steps: List<FlowStep>): Boolean {
            for (view in stepsFor(steps, answers)) {
                val choice = view.step.choice
                if (choice == null) {
                    items += FlowItem.Step(view, ++number)
                    continue
                }
                hasChoice = true
                val selected = choice.options.firstOrNull { (answers[choice.field] as? Answer.Single)?.value == it.value }
                items += FlowItem.Choice(choice, selected)
                if (selected == null) return false
                chosen += selected
                if (!walk(selected.steps)) return false
            }
            return true
        }
        val decided = walk(proc.steps)
        val amounts = when {
            !decided -> emptyList()
            hasChoice -> chosen.flatMap { it.amounts }
            else -> proc.amounts
        }
        return Flow(items, decided, amounts)
    }

    private fun visibleSteps(proc: ProcedureDef, answers: Map<String, Answer>): List<StepView> = stepsFor(proc.steps, answers)

    /** 当てはまらない条件のあるステップを除く。未回答の条件だけなら「当てはまる場合のみ」として残す。 */
    private fun stepsFor(steps: List<FlowStep>, answers: Map<String, Answer>): List<StepView> = steps.mapNotNull { step ->
        val results = step.conditions.map { it.field to check(it, answers[it.field]) }
        if (results.any { it.second == Tri.FALSE }) null
        else StepView(step, results.filter { it.second == Tri.UNKNOWN }.map { it.first }.distinct())
    }

    /** 関連度順（点数の高い順）か期限順（期限の近い順。期限の無いものは最後、同じなら点数の高い順）に並べる。 */
    fun sort(items: List<Scored>, order: SortOrder, today: LocalDate = LocalDate.now()): List<Scored> = when (order) {
        SortOrder.RELEVANCE -> items.sortedByDescending { it.score }
        SortOrder.DEADLINE -> items.sortedWith(
            compareBy<Scored, LocalDate?>(nullsLast()) { it.procedure.due?.dateFrom(today) }.thenByDescending { it.score },
        )
    }

    private val placeholder = Regex("""\{(\w+)\}""")

    /**
     * 説明文の {annual_income} などを、その人の回答（「140万円」「20歳」）に置き換える。
     * 未回答なら「（年収は未入力）」にする。回答は端末の中で使うだけで、送らない。
     */
    fun fillIn(text: String, rawAnswers: Map<String, Answer>, questions: List<Question>, today: LocalDate = LocalDate.now()): String {
        if ('{' !in text) return text
        val answers = withDerived(rawAnswers, today)
        return placeholder.replace(text) { m ->
            val field = m.groupValues[1]
            val question = questions.firstOrNull { it.field == field }
            val answer = answers[field]
            when {
                question != null && answer != null -> question.describe(answer)
                answer is Answer.Range && answer.min == answer.max -> "${answer.min}歳" // 生年月日から計算した age・age_at_year_end
                else -> "（${questionFor(field, questions)?.name ?: field}は未入力）"
            }
        }
    }

    /** 項目に答えるための質問（age なら生年月日の質問）。 */
    fun questionFor(field: String, questions: List<Question>): Question? =
        questions.firstOrNull { it.field == field } ?: questions.firstOrNull { field in it.provides }

    fun check(cond: Condition, answer: Answer?): Tri = when (answer) {
        null -> Tri.UNKNOWN
        is Answer.Single -> checkValue(cond, answer.value)
        is Answer.Multi -> checkValues(cond, answer.values)
        is Answer.Range -> checkRange(cond, answer.min, answer.max)
        is Answer.Number -> checkRange(cond, answer.value, answer.value)
        is Answer.Date -> Tri.UNKNOWN // 日付は age などに計算してから判定する
    }

    /** 複数選択: eq / in は「選んだどれかが当てはまる」、ne / not_in は「選んだどれも当てはまらない」（engine.py と同じ）。 */
    private fun checkValues(cond: Condition, values: List<String>): Tri = when (cond.op) {
        "contains", "eq" -> tri(cond.value.text() in values)
        "ne" -> tri(cond.value.text() !in values)
        "in" -> tri(values.any { it in cond.value.texts() })
        "not_in" -> tri(values.none { it in cond.value.texts() })
        else -> Tri.UNKNOWN
    }

    private fun checkValue(cond: Condition, v: String): Tri = when (cond.op) {
        "eq" -> tri(v == cond.value.text())
        "ne" -> tri(v != cond.value.text())
        "in" -> tri(v in cond.value.texts())
        "not_in" -> tri(v !in cond.value.texts())
        else -> Tri.UNKNOWN
    }

    /** 回答は範囲 [min, max] なので、範囲のどの値でも結果が同じときだけ TRUE / FALSE を返す。 */
    private fun checkRange(cond: Condition, min: Long, max: Long?): Tri {
        val hi = max ?: Long.MAX_VALUE
        return when (cond.op) {
            "gte" -> cond.value.num()?.let { v -> decide(min >= v, hi < v) }
            "gt" -> cond.value.num()?.let { v -> decide(min > v, hi <= v) }
            "lte" -> cond.value.num()?.let { v -> decide(hi <= v, min > v) }
            "lt" -> cond.value.num()?.let { v -> decide(hi < v, min >= v) }
            "eq", "in" -> inSet(cond.value.nums(), min, hi)
            "ne", "not_in" -> inSet(cond.value.nums(), min, hi).not()
            else -> null
        } ?: Tri.UNKNOWN
    }

    private fun inSet(values: List<Long>, min: Long, hi: Long): Tri = when {
        min == hi && min in values -> Tri.TRUE
        values.none { it in min..hi } -> Tri.FALSE
        else -> Tri.UNKNOWN
    }

    private fun decide(isTrue: Boolean, isFalse: Boolean) = when {
        isTrue -> Tri.TRUE
        isFalse -> Tri.FALSE
        else -> Tri.UNKNOWN
    }

    private fun tri(b: Boolean) = if (b) Tri.TRUE else Tri.FALSE

    private fun Tri.not() = when (this) {
        Tri.TRUE -> Tri.FALSE
        Tri.FALSE -> Tri.TRUE
        Tri.UNKNOWN -> Tri.UNKNOWN
    }

    private fun JsonElement.text(): String = jsonPrimitive.content
    private fun JsonElement.texts(): List<String> = if (this is JsonArray) map { it.text() } else listOf(text())
    private fun JsonElement.num(): Long? = (this as? JsonPrimitive)?.longOrNull
    private fun JsonElement.nums(): List<Long> = if (this is JsonArray) mapNotNull { it.num() } else listOfNotNull(num())
}
