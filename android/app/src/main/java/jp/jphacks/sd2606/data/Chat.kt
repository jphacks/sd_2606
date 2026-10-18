package jp.jphacks.sd2606.data

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// backend/app/llm/chat.py の ChatRequest / ChatReply と対応させること。
// 「AIに質問」だけは回答をサーバーに送る（Claude に渡すため）。送るのは表示用に加工したもの（生年月日ではなく年齢）で、サーバーには保存しない。

@Serializable
data class ChatRequest(
    @SerialName("procedure_id") val procedureId: String?,
    val profile: List<ProfileItem>,
    val procedures: List<ProcedureState>,
    val messages: List<ChatMessage>,
)

@Serializable
data class ProfileItem(val label: String, val value: String)

@Serializable
data class ProcedureState(val id: String, val status: String, val reasons: List<String> = emptyList(), val completed: Boolean = false)

@Serializable
data class ChatMessage(val role: String, val content: String)

/**
 * AI の返事。kind が question なら、ユーザーの状態を聞き返している（options から選んでもらう）。
 * field があれば、その質問はアプリの質問項目に当たり、選んだ option の value を回答として保存できる。
 */
@Serializable
data class ChatReply(
    val kind: String,
    val text: String,
    val field: String? = null,
    val options: List<ReplyOption> = emptyList(),
) {
    val isQuestion: Boolean get() = kind == "question"
}

@Serializable
data class ReplyOption(val label: String, val value: String? = null)

/** AI に送るユーザーの情報。画面の「AIに伝える情報」にもこのまま出す。 */
data class ChatContext(val procedureId: String?, val profile: List<ProfileItem>, val procedures: List<ProcedureState>) {
    companion object {
        /**
         * 端末の回答を、AI に送る形にする。
         * - 生年月日は送らず、今日の年齢と年末時点の年齢だけにする
         * - 数値や選択肢は画面と同じ表記（「160万円」「親の扶養に入っている」）にする
         * - フローの分岐で選んだもの（学生納付特例を使う、など）も、分岐の見出しと選択肢の名前で送る
         */
        fun build(
            catalog: Catalog,
            answers: Map<String, Answer>,
            evaluation: Evaluation?,
            completed: Map<String, String>,
            procedureId: String?,
            today: LocalDate = LocalDate.now(),
        ): ChatContext {
            val profile = buildList {
                val parts = catalog.questions.filter { it.partOf != null }
                for (q in catalog.questions) {
                    if (q.partOf != null) continue // 親の項目の値に添える
                    val answer = answers[q.field] ?: continue
                    if (answer is Answer.Date) {
                        val birth = runCatching { answer.toLocalDate() }.getOrNull() ?: continue
                        val endOfYear = LocalDate.of(today.year, 12, 31)
                        add(ProfileItem("年齢", "${RuleEngine.ageOn(birth, today)}歳（今年の12月31日時点 ${RuleEngine.ageOn(birth, endOfYear)}歳）"))
                    } else {
                        val extra = parts.filter { it.partOf == q.field }.mapNotNull { p -> answers[p.field]?.let { "（${p.describe(it)}）" } }
                        add(ProfileItem(q.name, RuleEngine.displayQuestion(q, answers).describe(answer) + extra.joinToString("")))
                    }
                }
                for (proc in catalog.procedures) {
                    for (choice in RuleEngine.allChoices(proc.steps)) {
                        val value = (answers[choice.field] as? Answer.Single)?.value ?: continue
                        val option = choice.options.firstOrNull { it.value == value } ?: continue
                        add(ProfileItem("${proc.title}：${choice.title}", option.label))
                    }
                }
            }
            val procedures = evaluation?.items.orEmpty().map {
                ProcedureState(
                    id = it.procedure.id,
                    status = it.status.name.lowercase(),
                    reasons = it.reasons,
                    completed = it.procedure.id in completed,
                )
            }
            return ChatContext(procedureId, profile, procedures)
        }
    }
}
