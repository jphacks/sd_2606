package jp.jphacks.sd2606.data

import java.time.LocalDate
import java.time.YearMonth
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
    /** 毎年・毎月の期限（通知と履歴の時系列に並べる） */
    val reminders: List<Reminder> = emptyList(),
    /** お金の目安（金額は端末内で回答から計算する。data/Amounts.kt） */
    val amounts: List<AmountSpec> = emptyList(),
)

/** 手続きに出すお金の目安の種類。card なら一覧のカードにも1行で出す。 */
@Serializable
data class AmountSpec(
    val kind: String,
    val card: Boolean = false,
    @SerialName("when") val conditions: List<Condition> = emptyList(),
)

/** 毎年・毎月の期限。month が null なら毎月。day がその月に無ければ月末。before_days 日前から「もうすぐ」と出す。 */
@Serializable
data class Reminder(
    val label: String,
    val month: Int? = null,
    val day: Int,
    @SerialName("before_days") val beforeDays: Int = 7,
    @SerialName("when") val conditions: List<Condition> = emptyList(),
) {
    /** from 以降（from を含む）で最初に来る期限の日付。 */
    fun nextDue(from: LocalDate): LocalDate {
        var ym = YearMonth.from(from)
        while (true) {
            if (month == null || ym.monthValue == month) {
                val due = ym.atDay(minOf(day, ym.lengthOfMonth()))
                if (!due.isBefore(from)) return due
            }
            ym = ym.plusMonths(1)
        }
    }
}

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
 * flag: required（必須）/ conditional（conditions をすべて満たせば必須、無ければ点数だけ。条件次第）
 *       / unrelated（対象外。conditions があればすべて満たしたときだけ。点数は0以下）
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

/**
 * 手続きの1ステップ。title / detail の {annual_income} などは、表示するときにその人の回答に置き換える（RuleEngine.fillIn）。
 * choice があるステップはフローの分岐（title / detail は空）。選ぶまで、この先のステップと金額は出さない。
 */
@Serializable
data class FlowStep(
    val title: String = "",
    val detail: String = "",
    val where: String? = null,
    /** このステップで必要なもの（手続きの「必要なもの」は全ステップ分をまとめる） */
    val documents: List<String> = emptyList(),
    /** 詳しい手順（箇条書き）。最初は閉じておき、開くと表示する */
    val details: List<String> = emptyList(),
    /** この説明が当てはまる人の条件。満たさない条件があれば表示しない（未回答なら「当てはまる場合のみ」として表示） */
    @SerialName("when") val conditions: List<Condition> = emptyList(),
    val choice: FlowChoice? = null,
)

/** フローの分岐。選んだ値は回答として field に保存する（端末の中だけ。期限や金額の when にも使える）。 */
@Serializable
data class FlowChoice(val field: String, val title: String, val options: List<FlowOption>)

/** 分岐の選択肢。選ぶと steps と amounts を出す。 */
@Serializable
data class FlowOption(
    val value: String,
    val label: String,
    val detail: String,
    val steps: List<FlowStep> = emptyList(),
    val amounts: List<AmountSpec> = emptyList(),
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
    /** true なら最初の画面で必ず答えてもらう（基本情報）。false は任意（最初の画面の「もっと詳しく」と、判定に必要になった手続きで聞く） */
    val required: Boolean = false,
    /** true なら最初の画面では聞かず、判定に必要になった手続きのカードの中でだけ質問文をそのまま出して聞く（掛け持ちなど） */
    val inline: Boolean = false,
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
