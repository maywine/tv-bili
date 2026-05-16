package dev.tvbili.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.repo.SearchRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 搜索屏 ViewModel：query StateFlow → 300ms debounce → SearchRepository → state。
 *
 * - 空 query（trim 后）= [SearchState.Idle]；不发请求
 * - submit() 由 IME `ImeAction.Search` 调；debounce 已覆盖大多数场景，submit 只是手动触发
 * - 用 ViewModel 而非 AndroidViewModel——无需 Application Context
 */
@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {

    private val repo = SearchRepository()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()
                .collect { kw -> runSearch(kw) }
        }
    }

    fun onQueryChange(q: String) {
        _query.value = q
    }

    /**
     * IME「搜索」键调。
     * 直接旁路 [_query] 触发 [runSearch]——StateFlow + distinctUntilChanged 会过滤
     * 重复值，否则用户对同一关键词按搜索键会变哑键。
     */
    fun submit() {
        val kw = _query.value.trim()
        if (kw.isNotEmpty()) viewModelScope.launch { runSearch(kw) }
    }

    private suspend fun runSearch(kw: String) {
        if (kw.isEmpty()) {
            _state.value = SearchState.Idle
            return
        }
        _state.value = SearchState.Loading(kw)
        repo.searchVideos(kw).fold(
            onSuccess = { list ->
                _state.value = if (list.isEmpty()) SearchState.Empty(kw)
                else SearchState.Loaded(kw, list)
            },
            onFailure = { e ->
                Log.e(TAG, "search($kw) failed", e)
                _state.value = SearchState.Error(kw, e.message ?: "搜索失败")
            },
        )
    }

    private companion object {
        const val TAG = "SearchVM"
        const val DEBOUNCE_MS = 300L
    }
}
