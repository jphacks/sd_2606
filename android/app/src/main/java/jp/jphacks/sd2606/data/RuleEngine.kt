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

/**
 * 一覧に出す手続き。required なら「必要な手続き」、そうでなければ「関係がありそうな手続き」。
 * missing は「関係がありそう」のうち、答えると必須かどうか決まる項目（条件次第の未回答の条件）。
 */
data class Scored(
    val procedure: ProcedureDef,
    val required: Boolean,
    val score: Double,
    val reasons: List<String>,
    val missing: List<String>,
)

data class Evaluation(val required: List<Scored>, val likely: List<Scored>) {
    fun find(id: String): Scored? = required.firstOrNull { it.procedure.id == id } ?: likely.firstOrNull { it.procedure.id == id }

    /** TRUE: 必要 / UNKNOWN: 関係がありそう / FALSE: 一覧に出ない */
    fun statusOf(id: String): Tri = when (find(id)?.required) {
        true -> Tri.TRUE
        false -> Tri.UNKNOWN
        null -> Tri.FALSE
    }
}

/**
 * 端末内でのルール判定（backend/app/rules/engine.py と同じ条件の意味）。
 *
 * 未回答の項目は「まだ分からない」として3値で判定し、必須かどうかを決めるのに足りない項目だけを聞く。
 * これにより、最初は最小限の質問だけで始め、必要になった情報だけを後から聞ける。
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
     * 手続きごとに relations を見て、必要（必須）・関係がありそう（点数がしきい値以上）・出さない、に分ける。
     *
     * - きっかけに当てはまる行だけを数える
     * - required の行があれば必須。conditional は conditions をすべて満たせば必須、満たさない条件があればその行は数えない、
     *   未回答の条件があれば点数だけ数えて、その項目を missing（答えると必須かどうか決まる）にする
     * - 必須でなければ、数えた行の点数の合計がしきい値以上なら「関係がありそう」。どちらも点数の高い順に並べる
     */
    fun evaluate(
        procedures: List<ProcedureDef>,
        rawAnswers: Map<String, Answer>,
        threshold: Double,
        today: LocalDate = LocalDate.now(),
    ): Evaluation {
        val answers = withDerived(rawAnswers, today)
        val required = mutableListOf<Scored>()
        val likely = mutableListOf<Scored>()
        for (proc in procedures) {
            var isRequired = false
            val counted = mutableListOf<Relation>()
            val missing = mutableListOf<String>()
            for (rel in proc.relations) {
                if (check(rel.trigger(), answers[rel.field]) != Tri.TRUE) continue
                when (rel.flag) {
                    "required" -> isRequired = true
                    "conditional" -> {
                        val results = rel.conditions.map { it to check(it, answers[it.field]) }
                        if (results.any { it.second == Tri.FALSE }) continue
                        val unknown = results.filter { it.second == Tri.UNKNOWN }.map { it.first.field }
                        if (results.isNotEmpty() && unknown.isEmpty()) isRequired = true else missing += unknown
                    }
                }
                counted += rel
            }
            val score = counted.sumOf { it.score }
            val reasons = counted.sortedByDescending { it.score }.mapNotNull { it.reason }.distinct()
            when {
                isRequired -> required += Scored(proc, true, score, reasons, emptyList())
                score >= threshold - 1e-9 -> likely += Scored(proc, false, score, reasons, missing.distinct())
            }
        }
        return Evaluation(required.sortedByDescending { it.score }, likely.sortedByDescending { it.score })
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
