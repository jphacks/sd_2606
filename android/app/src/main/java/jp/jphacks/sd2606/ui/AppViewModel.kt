package jp.jphacks.sd2606.ui

import android.app.Application
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
    /** catalog と answers から端末内で判定した結果 */
    val evaluation: Evaluation? = null,
)

/**
 * 画面をまたいで制度データと回答を持つ ViewModel（Activity スコープで共有）。
 * サーバーとの通信は制度データの取得だけで、回答は端末内で判定に使うだけ。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LocalStore(app)
    private val _state = MutableStateFlow(AppUiState(answers = store.loadAnswers()))
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
                    catalog != null -> it.copy(catalog = UiResult.Success(catalog), offline = fetched.isFailure).evaluated()
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

    /** 最初の画面の「調べる」: 何も選ばなかったライフイベントなど（noneIfEmpty）は「該当なし」として確定させる。 */
    fun submitInitial() {
        val catalog = (_state.value.catalog as? UiResult.Success)?.data ?: return
        catalog.questions
            .filter { it.initial && it.noneIfEmpty && it.field !in _state.value.answers }
            .forEach { answer(it.field, Answer.Multi(emptyList())) }
    }

    fun clearAnswers() {
        store.clearAnswers()
        _state.update { it.copy(answers = emptyMap()).evaluated() }
    }

    private fun AppUiState.evaluated(): AppUiState {
        val catalog = (catalog as? UiResult.Success)?.data ?: return this
        return copy(evaluation = RuleEngine.evaluate(catalog.procedures, answers))
    }

    private fun Throwable?.toMessage() =
        "制度データを取得できませんでした（${this?.let { it::class.simpleName } ?: ""}: ${this?.message ?: ""}）"
}
