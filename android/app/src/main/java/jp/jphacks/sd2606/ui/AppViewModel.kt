package jp.jphacks.sd2606.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.jphacks.sd2606.data.ApiClient
import jp.jphacks.sd2606.data.ApiService
import jp.jphacks.sd2606.data.Procedure
import jp.jphacks.sd2606.data.RecommendationResponse
import jp.jphacks.sd2606.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiResult<out T> {
    data object Idle : UiResult<Nothing>
    data object Loading : UiResult<Nothing>
    data class Success<T>(val data: T) : UiResult<T>
    data class Error(val message: String) : UiResult<Nothing>
}

data class AppUiState(
    val profile: UserProfile = UserProfile(),
    val recommendations: UiResult<RecommendationResponse> = UiResult.Idle,
    val procedure: UiResult<Procedure> = UiResult.Idle,
)

/** 画面をまたいで入力内容と結果を持つ ViewModel（Activity スコープで共有）。 */
class AppViewModel(private val api: ApiService = ApiClient.service) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun updateProfile(transform: (UserProfile) -> UserProfile) {
        _state.update { it.copy(profile = transform(it.profile)) }
    }

    fun loadRecommendations() {
        _state.update { it.copy(recommendations = UiResult.Loading) }
        viewModelScope.launch {
            val result = runCatching { api.recommendations(_state.value.profile) }
            _state.update {
                it.copy(recommendations = result.fold({ r -> UiResult.Success(r) }, { e -> UiResult.Error(e.toMessage()) }))
            }
        }
    }

    fun loadProcedure(id: String) {
        val current = _state.value.procedure
        if (current is UiResult.Success && current.data.id == id) return
        _state.update { it.copy(procedure = UiResult.Loading) }
        viewModelScope.launch {
            val result = runCatching { api.procedure(id) }
            _state.update {
                it.copy(procedure = result.fold({ p -> UiResult.Success(p) }, { e -> UiResult.Error(e.toMessage()) }))
            }
        }
    }

    private fun Throwable.toMessage() = "通信に失敗しました（${this::class.simpleName}: ${message ?: ""}）"
}
