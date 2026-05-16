package dev.tvbili.ui.favorite

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.model.FavFolder
import dev.tvbili.data.repo.FavoriteRepository
import dev.tvbili.data.repo.HomeCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 收藏夹首屏：折叠的状态机。 */
sealed interface FavoriteState {
    data object Idle : FavoriteState
    data object Loading : FavoriteState
    data class Folders(val folders: List<FavFolder>) : FavoriteState
    data class Error(val message: String) : FavoriteState
}

/** 单个收藏夹内的视频列表态。 */
sealed interface FavoriteFolderState {
    data object Idle : FavoriteFolderState
    data object Loading : FavoriteFolderState
    data class Ready(
        val folderId: Long,
        val folderTitle: String,
        val cards: List<HomeCard.Video>,
        val page: Int,
        val hasMore: Boolean,
        val appending: Boolean = false,
    ) : FavoriteFolderState
    data class Error(val message: String) : FavoriteFolderState
}

class FavoriteViewModel : ViewModel() {

    private val repo = FavoriteRepository()

    private val _state = MutableStateFlow<FavoriteState>(FavoriteState.Idle)
    val state: StateFlow<FavoriteState> = _state.asStateFlow()

    private val _folderState = MutableStateFlow<FavoriteFolderState>(FavoriteFolderState.Idle)
    val folderState: StateFlow<FavoriteFolderState> = _folderState.asStateFlow()

    fun loadFolders() {
        if (_state.value is FavoriteState.Loading) return
        _state.value = FavoriteState.Loading
        viewModelScope.launch {
            _state.value = repo.listFolders().fold(
                onSuccess = { FavoriteState.Folders(it) },
                onFailure = { e ->
                    Log.e(TAG, "listFolders failed", e)
                    FavoriteState.Error(e.message ?: "加载失败")
                },
            )
        }
    }

    fun openFolder(folder: FavFolder) {
        _folderState.value = FavoriteFolderState.Loading
        viewModelScope.launch {
            _folderState.value = repo.listFolderVideos(folder.id, page = 1).fold(
                onSuccess = { (cards, hasMore) ->
                    FavoriteFolderState.Ready(
                        folderId = folder.id,
                        folderTitle = folder.title,
                        cards = cards,
                        page = 1,
                        hasMore = hasMore,
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "openFolder(${folder.id}) failed", e)
                    FavoriteFolderState.Error(e.message ?: "加载失败")
                },
            )
        }
    }

    fun loadMoreInFolder() {
        val cur = _folderState.value as? FavoriteFolderState.Ready ?: return
        if (cur.appending || !cur.hasMore) return
        _folderState.value = cur.copy(appending = true)
        viewModelScope.launch {
            val next = cur.page + 1
            val result = repo.listFolderVideos(cur.folderId, page = next)
            val latest = _folderState.value as? FavoriteFolderState.Ready ?: return@launch
            _folderState.value = result.fold(
                onSuccess = { (more, hasMore) ->
                    val existing = latest.cards.mapTo(mutableSetOf()) { it.bvid }
                    val appended = more.filter { it.bvid !in existing }
                    latest.copy(
                        cards = latest.cards + appended,
                        page = next,
                        hasMore = hasMore,
                        appending = false,
                    )
                },
                onFailure = { e ->
                    Log.w(TAG, "loadMoreInFolder failed: ${e.message}")
                    latest.copy(appending = false)
                },
            )
        }
    }

    fun clearFolder() {
        _folderState.value = FavoriteFolderState.Idle
    }

    private companion object {
        const val TAG = "FavoriteVM"
    }
}
