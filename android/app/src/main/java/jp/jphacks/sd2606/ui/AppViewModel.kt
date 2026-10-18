package jp.jphacks.sd2606.ui

import android.app.Application
import java.time.LocalDate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.data.ApiClient
import jp.jphacks.sd2606.data.Catalog
import jp.jphacks.sd2606.data.Evaluation
import jp.jphacks.sd2606.data.LocalStore
import jp.jphacks.sd2606.data.RuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiResult<out T> {
    data object Loading : UiResult<Nothing>
    data class Success<T>(val data: T) : UiResult<T>
    data class Error(val message: String) : UiResult<Nothing>
}

data class AppUiState(
    val catalog: UiResult<Catalog> = UiResult.Loading,
    /** 通信できず、前回保存した制度データを使っている */
    val offline: Boolean = false,
    val answers: Map<String, Answer> = emptyMap(),
    /** 完了した手続き（id → 完了した日。ISO 形式） */
    val completed: Map<String, String> = emptyMap(),
    /** 本人が「自分には関係ない」にした手続きの id */
    val dismissed: Set<String> = emptySet(),
    /** 本人が「当てはまる」にした手続きの id */
    val confirmed: Set<String> = emptySet(),
    /** catalog と answers から端末内で判定した結果 */
    val evaluation: Evaluation? = null,
)

/**
 * 画面をまたいで制度データと回答を持つ ViewModel（Activity スコープで共有）。
 * サーバーとの通信は制度データの取得だけで、回答は端末内で判定に使うだけ。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val _state = MutableStateFlow(
        AppUiState(
            answers = store.loadAnswers(),
            completed = store.loadCompleted(),
            dismissed = store.loadDismissed(),
            confirmed = store.loadConfirmed(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        _state.update { it.copy(catalog = UiResult.Loading) }
        viewModelScope.launch {
            val fetched = runCatching { ApiClient.service.catalog() }
            val catalog = withContext(Dispatchers.IO) {
                fetched.onSuccess(store::saveCatalog).getOrNull() ?: store.loadCatalog()
            }
            _state.update {
                when {
                    catalog != null -> it.copy(catalog = UiResult.Success(catalog), offline = fetched.isFailure, answers = it.answers.known(catalog))
                        .evaluated()
                    else -> it.copy(catalog = UiResult.Error(fetched.exceptionOrNull().toMessage()))
                }
            }
        }
    }

    /** 回答を記録する（null なら取り消す）。端末に保存し、その場で判定し直す。 */
    fun answer(field: String, answer: Answer?) {
        _state.update {
            val answers = if (answer == null) it.answers - field else it.answers + (field to answer)
            store.saveAnswers(answers)
            it.copy(answers = answers).evaluated()
        }
    }

    /** 最初の画面の「調べる」: 何も選ばなかった noneIfEmpty の質問は「該当なし」として確定させる。 */
    fun submitInitial() {
        val catalog = (_state.value.catalog as? UiResult.Success)?.data ?: return
        catalog.questions
            .filter { it.required && it.noneIfEmpty && it.field !in _state.value.answers }
            .forEach { answer(it.field, Answer.Multi(emptyList())) }
    }

    /** 手続きを完了にする（今日の日付で記録する）か、未完了に戻す。 */
    fun setCompleted(id: String, done: Boolean) {
        _state.update {
            val completed = if (done) it.completed + (id to LocalDate.now().toString()) else it.completed - id
            store.saveCompleted(completed)
            it.copy(completed = completed)
        }
    }

    fun clearCompleted() {
        store.saveCompleted(emptyMap())
        _state.update { it.copy(completed = emptyMap()) }
    }

    /** 「自分には関係ない」にする（対象外のグループに移す）か、元に戻す。「当てはまる」にしていたら取り消す。 */
    fun setDismissed(id: String, dismissed: Boolean) {
        _state.update {
            val ids = if (dismissed) it.dismissed + id else it.dismissed - id
            val confirmed = if (dismissed) it.confirmed - id else it.confirmed
            store.saveDismissed(ids)
            store.saveConfirmed(confirmed)
            it.copy(dismissed = ids, confirmed = confirmed).evaluated()
        }
    }

    /** 「当てはまる」にする（必要な手続きのグループに移す）か、元に戻す。「関係ない」にしていたら取り消す。 */
    fun setConfirmed(id: String, confirmed: Boolean) {
        _state.update {
            val ids = if (confirmed) it.confirmed + id else it.confirmed - id
            val dismissed = if (confirmed) it.dismissed - id else it.dismissed
            store.saveConfirmed(ids)
            store.saveDismissed(dismissed)
            it.copy(confirmed = ids, dismissed = dismissed).evaluated()
        }
    }

    /** 「関係ない」「当てはまる」の記録を消し、ルールの判定に戻す。 */
    fun clearDismissed() {
        store.saveDismissed(emptySet())
        store.saveConfirmed(emptySet())
        _state.update { it.copy(dismissed = emptySet(), confirmed = emptySet()).evaluated() }
    }

    /** 端末に保存している回答・完了の記録・「関係ない」「当てはまる」の記録をすべて消す。 */
    /**
     * 「あなたの手続き」で答えた情報（掛け持ち・年末の在籍・健康保険・分岐で選んだものなど）を消す。
     * 最初の画面で答える必須の項目（アルバイト・生年月日・扶養・年収と見込み / 確定）は残す。
     */
    fun resetProcedureAnswers() {
        val catalog = (_state.value.catalog as? UiResult.Success)?.data ?: return
        _state.update {
            val answers = RuleEngine.initialAnswers(it.answers, catalog)
            store.saveAnswers(answers)
            it.copy(answers = answers).evaluated()
        }
    }

    fun clearAll() {
        store.clearAll()
        _state.update { it.copy(answers = emptyMap(), completed = emptyMap(), dismissed = emptySet(), confirmed = emptySet()).evaluated() }
    }

    /**
     * いまの質問に無い項目の回答（以前の版で聞いていた「最近あったこと」など）を端末から消す。
     * 画面に出ない情報を持ち続けないため。
     */
    private fun Map<String, Answer>.known(catalog: Catalog): Map<String, Answer> {
        val fields = catalog.questions.map { it.field }.toSet() + RuleEngine.choiceFields(catalog.procedures)
        val kept = filterKeys { it in fields }
        if (kept.size != size) store.saveAnswers(kept)
        return kept
    }

    private fun AppUiState.evaluated(): AppUiState {
        val catalog = (catalog as? UiResult.Success)?.data ?: return this
        return copy(evaluation = RuleEngine.evaluate(catalog.procedures, answers, catalog.threshold, dismissed = dismissed, confirmed = confirmed))
    }

    private fun Throwable?.toMessage() =
        "制度データを取得できませんでした（${this?.let { it::class.simpleName } ?: ""}: ${this?.message ?: ""}）"
}
