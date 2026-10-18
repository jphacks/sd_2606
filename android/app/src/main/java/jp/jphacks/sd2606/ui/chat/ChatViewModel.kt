package jp.jphacks.sd2606.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import jp.jphacks.sd2606.data.ApiClient
import jp.jphacks.sd2606.data.ChatContext
import jp.jphacks.sd2606.data.ChatMessage
import jp.jphacks.sd2606.data.ChatReply
import jp.jphacks.sd2606.data.ChatRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/** 会話の1つ。AI の返事なら reply（聞き返しの選択肢など）を持つ。 */
data class ChatEntry(val fromUser: Boolean, val text: String, val reply: ChatReply? = null)

data class ChatThread(
    val entries: List<ChatEntry> = emptyList(),
    val loading: Boolean = false,
    /** 送れなかったときの説明。再送できる */
    val error: String? = null,
)

/**
 * 「AIに質問」の会話。手続きごと（下部タブからは手続きなし）に会話を分けて持つ。
 * 会話は端末のメモリにだけ置き、アプリを閉じると消える。
 */
class ChatViewModel : ViewModel() {
    private val _threads = MutableStateFlow<Map<String, ChatThread>>(emptyMap())
    val threads: StateFlow<Map<String, ChatThread>> = _threads.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    /**
     * 質問を送る。context は送る直前の回答から作る（聞き返しに答えて回答を保存した直後でも、新しい回答を送るため）。
     */
    fun send(text: String, context: () -> ChatContext) {
        val question = text.trim()
        if (question.isEmpty()) return
        val key = context().procedureId.key()
        if (_threads.value[key]?.loading == true) return
        update(key) { it.copy(entries = it.entries + ChatEntry(fromUser = true, text = question), error = null) }
        request(key, context)
    }

    /** 送れなかった質問を送り直す。 */
    fun retry(context: () -> ChatContext) {
        val key = context().procedureId.key()
        if (_threads.value[key]?.entries?.lastOrNull()?.fromUser == true) request(key, context)
    }

    /** 会話を最初からにする。 */
    fun clear(procedureId: String?) {
        val key = procedureId.key()
        jobs.remove(key)?.cancel()
        _threads.update { it - key }
    }

    private fun request(key: String, context: () -> ChatContext) {
        update(key) { it.copy(loading = true, error = null) }
        jobs[key] = viewModelScope.launch {
            val ctx = context()
            val messages = _threads.value[key]?.entries.orEmpty().map {
                ChatMessage(role = if (it.fromUser) "user" else "assistant", content = it.historyText())
            }
            val result = runCatching {
                ApiClient.chatService.chat(ChatRequest(ctx.procedureId, ctx.profile, ctx.procedures, messages.takeLast(MAX_MESSAGES).dropWhile { it.role == "assistant" }))
            }
            update(key) { thread ->
                result.fold(
                    onSuccess = { reply -> thread.copy(entries = thread.entries + ChatEntry(false, reply.text, reply), loading = false) },
                    onFailure = { e -> thread.copy(loading = false, error = e.toMessage()) },
                )
            }
        }
    }

    private fun update(key: String, f: (ChatThread) -> ChatThread) =
        _threads.update { it + (key to f(it[key] ?: ChatThread())) }

    companion object {
        /** サーバーが受け付ける会話の長さ（backend の ChatRequest.messages の max_length） */
        private const val MAX_MESSAGES = 30

        fun String?.key(): String = this ?: ""

        /** 聞き返しの選択肢も履歴に残し、AI が何を聞いたか分かるようにする。 */
        private fun ChatEntry.historyText(): String {
            val options = reply?.options.orEmpty()
            return if (options.isEmpty()) text else text + "\n（選択肢: " + options.joinToString(" / ") { it.label } + "）"
        }

        private fun Throwable.toMessage(): String = when (this) {
            // サーバーの説明（「AI の設定がありません」など）があればそれを出す
            is HttpException -> runCatching {
                val body = response()?.errorBody()?.string().orEmpty()
                Json.parseToJsonElement(body).jsonObject["detail"]!!.jsonPrimitive.content
            }.getOrElse { "送れませんでした（HTTP ${code()}）" }
            is IOException -> "通信できませんでした。接続を確かめてもう一度送ってください"
            else -> "送れませんでした（${this::class.simpleName}）"
        }
    }
}
