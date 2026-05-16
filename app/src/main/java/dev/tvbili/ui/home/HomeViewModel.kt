package dev.tvbili.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.repo.HistoryRepository
import dev.tvbili.data.repo.HomeRepository
import dev.tvbili.data.store.SectionConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HomeRepository()
    private val historyRepo = HistoryRepository(application)

    /** 用户选定分区（reactive）。SideBar / HomeScreen 都从这里读。 */
    val sections: StateFlow<List<SectionId>> =
        SectionConfigStore.flow(application).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SectionConfigStore.current,
        )

    private val _selectedSection = MutableStateFlow(
        sections.value.firstOrNull() ?: SectionId.RCMD,
    )
    val selectedSection: StateFlow<SectionId> = _selectedSection.asStateFlow()

    /**
     * 用户点卡片去看视频/直播时拨为 `true`；返回首页后由 ContentPane 消费——
     * grid 自己再向 [Modifier.focusRestorer] 借助 grid 已记忆的上次焦点子项把焦点还回去。
     *
     * 这条信号唯一目的：抑制 SideBar 在「从详情页回来」的那次合成里再次抢焦点。
     * 从 Settings/未点击场景回来时此值=false，SideBar 仍正常拿初始焦点。
     */
    private val _pendingGridFocus = MutableStateFlow(false)
    val pendingGridFocus: StateFlow<Boolean> = _pendingGridFocus.asStateFlow()

    /** 按需懒建：进入分区才创建对应 StateFlow，避免 12 个分区全部初始 = Idle 浪费内存。 */
    private val states = ConcurrentHashMap<SectionId, MutableStateFlow<SectionState>>()

    private var rcmdFreshIdx = 1

    init {
        load(_selectedSection.value)

        // 当用户在 Settings 删除当前选中的分区，回首页时自动归位到首项
        viewModelScope.launch {
            sections.collect { list ->
                if (list.isEmpty()) return@collect
                if (_selectedSection.value !in list) {
                    _selectedSection.value = list.first()
                    load(list.first())
                }
            }
        }
    }

    fun stateOf(section: SectionId): StateFlow<SectionState> =
        states.getOrPut(section) { MutableStateFlow(SectionState.Idle) }.asStateFlow()

    fun selectSection(section: SectionId) {
        _selectedSection.value = section
        val current = stateFor(section).value
        val stale = current is SectionState.Loaded &&
            System.currentTimeMillis() - current.loadedAtMs > STALE_AFTER_MS
        if (current is SectionState.Idle || current is SectionState.Error || stale) {
            load(section)
        }
    }

    fun retry(section: SectionId) = load(section)

    fun markCardClicked() { _pendingGridFocus.value = true }
    fun consumePendingGridFocus() { _pendingGridFocus.value = false }

    private fun stateFor(section: SectionId): MutableStateFlow<SectionState> =
        states.getOrPut(section) { MutableStateFlow(SectionState.Idle) }

    private fun load(section: SectionId) {
        val flow = stateFor(section)

        // PLACEHOLDER 不发请求，直接占位提示
        if (section.kind == SectionId.Kind.PLACEHOLDER) {
            flow.value = SectionState.Empty(
                hint = when (section) {
                    SectionId.FAVORITE -> "收藏夹支持暂未规划"
                    else -> "即将上线"
                },
            )
            return
        }

        flow.value = SectionState.Loading
        viewModelScope.launch {
            val result = when (section.kind) {
                SectionId.Kind.RECOMMEND ->
                    repo.loadRecommend(rcmdFreshIdx).also { rcmdFreshIdx++ }
                SectionId.Kind.POPULAR -> repo.loadPopular(page = 1)
                SectionId.Kind.LIVE -> repo.loadLive(page = 1)
                SectionId.Kind.RANKING -> repo.loadRanking(rid = section.rid)
                SectionId.Kind.HISTORY -> runCatching { historyRepo.getRecent() }
                SectionId.Kind.PLACEHOLDER -> return@launch // 上面已 return
            }
            flow.value = result.fold(
                onSuccess = { SectionState.Loaded(it) },
                onFailure = { e ->
                    Log.e(TAG, "load($section) failed", e)
                    SectionState.Error(e.message ?: "未知错误")
                },
            )
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"

        /**
         * 列表数据视为「新鲜」的时长——超过则下次 selectSection 自动重拉。
         * 5 分钟既能避免来回点同一 tab 反复发请求，又能在隔夜回到 app 时刷出当天的热门。
         */
        const val STALE_AFTER_MS = 5L * 60 * 1000
    }
}
