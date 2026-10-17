package jp.jphacks.sd2606.ui.board

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import jp.jphacks.sd2606.data.ApiClient
import jp.jphacks.sd2606.data.BoardMeta
import jp.jphacks.sd2606.data.BoardStore
import jp.jphacks.sd2606.data.BoardSummary
import jp.jphacks.sd2606.data.NewPost
import jp.jphacks.sd2606.data.Post
import jp.jphacks.sd2606.data.PostPage
import jp.jphacks.sd2606.data.PostQuery
import jp.jphacks.sd2606.ui.UiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

data class BoardState(
    val meta: UiResult<BoardMeta> = UiResult.Loading,
    /** 手続きごとの体験談の件数（おすすめ一覧のカード用） */
    val counts: Map<String, Int> = emptyMap(),
    /** 投稿・削除のたびに増やす。一覧はこれが変わったら読み直す */
    val version: Int = 0,
    /** 一覧を読み直さずに「役に立った」の数を反映するための上書き */
    val helpfulCounts: Map<Int, Int> = emptyMap(),
    val helpful: Set<Int> = emptySet(),
    val reported: Set<Int> = emptySet(),
    val mine: Set<Int> = emptySet(),
)

/** 掲示板の通信と、端末に保存する自分の操作（Activity スコープで共有）。 */
class BoardViewModel(app: Application) : AndroidViewModel(app) {
    private val store = BoardStore(app)
    private val api = ApiClient.board
    private val _state = MutableStateFlow(BoardState(helpful = store.helpful(), reported = store.reported(), mine = store.myPosts().keys))
    val state: StateFlow<BoardState> = _state.asStateFlow()

    init {
        loadMeta()
    }

    fun loadMeta() {
        _state.update { it.copy(meta = UiResult.Loading) }
        viewModelScope.launch {
            val meta = runCatching { api.meta() }
            _state.update { it.copy(meta = meta.fold({ m -> UiResult.Success(m) }, { e -> UiResult.Error(e.userMessage()) })) }
            refreshCounts()
        }
    }

    private suspend fun refreshCounts() {
        runCatching { api.counts() }.onSuccess { c -> _state.update { it.copy(counts = c) } }
    }

    suspend fun page(q: PostQuery, offset: Int): Result<PostPage> =
        runCatching { api.posts(q.procedure, q.tag, q.kind, q.sort, offset) }.withMessage()

    suspend fun summary(procedureId: String): Result<BoardSummary> = runCatching { api.summary(procedureId) }.withMessage()

    suspend fun post(id: Int): Result<Post> = runCatching { api.post(id) }.withMessage()

    suspend fun submit(post: NewPost): Result<Post> = runCatching {
        val created = api.create(post)
        store.saveMyPost(created.post.id, created.deleteToken)
        _state.update { it.copy(mine = it.mine + created.post.id, version = it.version + 1) }
        refreshCounts()
        created.post
    }.withMessage()

    suspend fun toggleHelpful(post: Post): Result<Int> {
        val on = post.id !in _state.value.helpful
        return runCatching {
            val count = if (on) api.helpful(post.id, store.clientId) else api.unhelpful(post.id, store.clientId)
            store.setHelpful(post.id, on)
            _state.update {
                it.copy(
                    helpful = if (on) it.helpful + post.id else it.helpful - post.id,
                    helpfulCounts = it.helpfulCounts + (post.id to count.helpful),
                )
            }
            count.helpful
        }.withMessage()
    }

    suspend fun report(id: Int): Result<Unit> = runCatching {
        api.report(id, store.clientId).ensureOk()
        store.addReported(id)
        _state.update { it.copy(reported = it.reported + id) }
    }.withMessage()

    suspend fun delete(id: Int): Result<Unit> = runCatching {
        val token = store.myPosts()[id] ?: error("この端末から投稿したものではありません")
        val res = api.delete(id, token)
        if (res.code() != 404) res.ensureOk() // 404 はすでに消えている
        store.forgetMyPost(id)
        _state.update { it.copy(mine = it.mine - id, version = it.version + 1) }
        refreshCounts()
    }.withMessage()

    private fun retrofit2.Response<Unit>.ensureOk() {
        if (!isSuccessful) throw HttpException(this)
    }

    private fun <T> Result<T>.withMessage(): Result<T> = recoverCatching { throw BoardException(it.userMessage()) }

    private fun Throwable.userMessage(): String = when (this) {
        is BoardException -> message.orEmpty()
        is HttpException -> response()?.errorBody()?.string()
            ?.let { body -> runCatching { ApiClient.json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content }.getOrNull() }
            ?: "入力内容を確認してください（${code()}）"
        is IOException -> "通信できませんでした。電波の状態を確認してください"
        else -> message ?: this::class.simpleName.orEmpty()
    }
}

class BoardException(message: String) : Exception(message)
