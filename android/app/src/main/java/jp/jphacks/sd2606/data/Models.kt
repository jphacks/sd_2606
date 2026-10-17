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
)

@Serializable
data class ProcedureDef(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val priority: Int,
    val deadline: String? = null,
    val office: String? = null,
    val steps: List<FlowStep> = emptyList(),
    val links: List<Link> = emptyList(),
    val match: List<MatchClause> = emptyList(),
)

/** when の条件をすべて満たせば、reason を理由として手続きが該当する。 */
@Serializable
data class MatchClause(val reason: String, val `when`: List<Condition>)

@Serializable
data class Condition(val field: String, val op: String, val value: JsonElement)

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
    /** single（1つ選ぶ）/ multi（複数選ぶ）/ range（数値の範囲を選ぶ）/ date（日付を選ぶ） */
    val kind: String,
    val text: String,
    /** true なら最初の画面で聞く。false は判定に必要になったときだけ聞く */
    val initial: Boolean = false,
    /** multi で何も選ばなかったとき、未回答ではなく「どれにも当てはまらない」とする */
    @SerialName("none_if_empty") val noneIfEmpty: Boolean = false,
    val choices: List<Choice> = emptyList(),
    /** この回答から端末内で計算する項目（birth_date → age, age_at_year_end） */
    val provides: List<String> = emptyList(),
)

@Serializable
data class Choice(
    val label: String,
    val value: String? = null,
    val min: Long? = null,
    /** null は上限なし */
    val max: Long? = null,
)
