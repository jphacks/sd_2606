package jp.jphacks.sd2606.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// backend/app/schemas.py の Catalog と対応させること。
// アプリはこれを GET /api/catalog で受け取り、判定は端末内（RuleEngine）で行う。ユーザーの回答は送らない。

@Serializable
data class Catalog(
    val procedures: List<ProcedureDef>,
    val questions: List<Question>,
    /** 必須でない手続きは、点数の合計がこの値以上なら「関係がありそうな手続き」に出す */
    val threshold: Double = 0.5,
)

@Serializable
data class ProcedureDef(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val deadline: String? = null,
    val office: String? = null,
    val steps: List<FlowStep> = emptyList(),
    val links: List<Link> = emptyList(),
    val relations: List<Relation> = emptyList(),
)

/**
 * きっかけ（field / op / value。例: ライフイベントに退職がある）に当てはまるときの、手続きとの関係。
 * flag: required（必須）/ conditional（conditions をすべて満たせば必須。条件次第）/ unrelated（無関係。点数は0以下）
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
    val documents: List<String> = emptyList(),
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
