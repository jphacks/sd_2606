package jp.jphacks.sd2606.data

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// backend/app/schemas.py の Catalog と対応させること。
// アプリはこれを GET /api/catalog で受け取り、判定は端末内（RuleEngine）で行う。ユーザーの回答は送らない。

@Serializable
data class Catalog(
    val procedures: List<ProcedureDef>,
    val questions: List<Question>,
    /** 必須でも対象外でもない手続きは、点数の合計がこの値以上なら「関係がありそうな手続き」に出す。null は下限なし（-inf） */
    val threshold: Double? = null,
)

@Serializable
data class ProcedureDef(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val deadline: String? = null,
    /** 期限順の並べ替えに使う期限。無ければ最後に並ぶ */
    val due: Due? = null,
    val office: String? = null,
    val steps: List<FlowStep> = emptyList(),
    val links: List<Link> = emptyList(),
    val relations: List<Relation> = emptyList(),
)

/** 期限。days（きっかけから何日以内）か、month / day（毎年の期限。day が無ければ月末）のどちらか。 */
@Serializable
data class Due(val days: Int? = null, val month: Int? = null, val day: Int? = null) {
    /** 並べ替え用の日付。days は今日をきっかけの日とみなし、month / day は今日以降で最初に来るその日。 */
    fun dateFrom(today: LocalDate): LocalDate? {
        if (days != null) return today.plusDays(days.toLong())
        if (month == null) return null
        fun on(year: Int): LocalDate = LocalDate.of(year, month, 1).let { it.withDayOfMonth(minOf(day ?: it.lengthOfMonth(), it.lengthOfMonth())) }
        return on(today.year).takeIf { !it.isBefore(today) } ?: on(today.year + 1)
    }
}

/**
 * きっかけ（field / op / value。例: 働き方が学生）に当てはまるときの、手続きとの関係。
 * flag: required（必須）/ conditional（conditions をすべて満たせば必須、無ければ点数だけ。条件次第）/ unrelated（対象外。点数は0以下）
 */
@Serializable
data class Relation(
    val field: String,
    val op: String = "eq",
    val value: JsonElement,
    val flag: String,
    /** 関係の強さ（-1〜1） */
    val score: Double,
    val reason: String? = null,
    @SerialName("when") val conditions: List<Condition> = emptyList(),
) {
    fun trigger(): Condition = Condition(field = field, op = op, value = value)
}

@Serializable
data class Condition(val field: String, val op: String = "eq", val value: JsonElement)

@Serializable
data class FlowStep(
    val title: String,
    val detail: String,
    val where: String? = null,
    /** このステップで必要なもの（手続きの「必要なもの」は全ステップ分をまとめる） */
    val documents: List<String> = emptyList(),
    /** この説明が当てはまる人の条件。満たさない条件があれば表示しない（未回答なら「当てはまる場合のみ」として表示） */
    @SerialName("when") val conditions: List<Condition> = emptyList(),
)

@Serializable
data class Link(val label: String, val url: String)

@Serializable
data class Question(
    val field: String,
    /** single（1つ選ぶ）/ multi（複数選ぶ）/ range（数値の範囲を選ぶ）/ number（数値を入れる）/ date（日付を選ぶ） */
    val kind: String,
    val text: String,
    /** 項目の短い名前（「判定には生年月日が必要です」の案内に使う） */
    val label: String = "",
    /** true なら最初の画面で聞く。false は判定に必要になったときだけ聞く */
    val initial: Boolean = false,
    /** multi で何も選ばなかったとき、未回答ではなく「どれにも当てはまらない」とする */
    @SerialName("none_if_empty") val noneIfEmpty: Boolean = false,
    val choices: List<Choice> = emptyList(),
    /** number の単位（例: 万円） */
    val unit: String = "",
    /** number の入力の単位。年収を万円で入れるなら 10000（保存は円） */
    val scale: Long = 1,
    /** この回答から端末内で計算する項目（birth_date → age, age_at_year_end） */
    val provides: List<String> = emptyList(),
) {
    /** 画面に出す項目名。label が無ければ質問文 */
    val name: String get() = label.ifEmpty { text }
}

@Serializable
data class Choice(
    val label: String,
    val value: String? = null,
    val min: Long? = null,
    /** null は上限なし */
    val max: Long? = null,
)
