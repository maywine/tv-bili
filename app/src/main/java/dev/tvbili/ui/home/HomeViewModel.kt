package dev.tvbili.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.repo.HistoryRepository
import dev.tvbili.data.repo.HomeCard
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

    /**
     * 用户在每个分区里最后点击的卡片 [HomeCard.stableKey]。返回首页后 [ContentPane]
     * 据此 scrollToItem + 把焦点钉到该卡，规避 HomeScreen 在 nav 切换中被销毁导致
     * `focusRestorer()` 的内部记忆失效（destroy → recompose 后 grid 焦点回首项）。
     */
    private val lastFocusedKey = ConcurrentHashMap<SectionId, String>()

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
        val ttl = staleTtlMs(section)
        val stale = current is SectionState.Loaded &&
            System.currentTimeMillis() - current.loadedAtMs > ttl
        if (current is SectionState.Idle || current is SectionState.Error || stale) {
            load(section)
        }
    }

    /**
     * 不同分区数据「新鲜」的口径不同：
     * - 推荐：30 s —— 内容变化最快，用户期望每次回来都能见到新内容
     * - 排行 / 番剧 / 电影 / 综艺 等 RANKING：5 min —— 排行榜分钟级波动
     * - 热门 / 直播：2 min
     * - HISTORY：永远 stale，重进必刷
     */
    private fun staleTtlMs(section: SectionId): Long = when (section.kind) {
        SectionId.Kind.RECOMMEND -> 30L * 1000
        SectionId.Kind.POPULAR -> 2L * 60 * 1000
        SectionId.Kind.LIVE -> 2L * 60 * 1000
        SectionId.Kind.RANKING -> 5L * 60 * 1000
        SectionId.Kind.HISTORY -> 0L
        SectionId.Kind.PLACEHOLDER -> Long.MAX_VALUE
    }

    fun retry(section: SectionId) = load(section)

    /**
     * 推荐流分页追加：滚到 grid 末尾时由 ContentPane 触发。
     * 非 RECOMMEND 分区 no-op（B 站 ranking/popular 不暴露稳定的分页接口，硬分页易拿重复）。
     *
     * - 仅在 Loaded 且非 appending 时启动；并发 selectSection 切走时按 cur 视图引用守恒，
     *   旧追加结果不会污染新分区。
     * - 失败：保持原 cards 不变，把 appending 翻回 false（不弹 UI 错误，下一次滚动重试）。
     */
    fun loadMore(section: SectionId) {
        if (section.kind != SectionId.Kind.RECOMMEND) return
        val flow = stateFor(section)
        val cur = flow.value as? SectionState.Loaded ?: return
        if (cur.appending) return
        flow.value = cur.copy(appending = true)
        viewModelScope.launch {
            val freshIdx = rcmdFreshIdx++
            val result = repo.loadRecommend(freshIdx)
            val current = flow.value as? SectionState.Loaded ?: return@launch
            flow.value = result.fold(
                onSuccess = { more ->
                    // 按 stableKey 去重，避免 B 站偶发重复推荐
                    val existingKeys = current.cards.mapTo(mutableSetOf()) { it.stableKey }
                    val appended = more.filter { it.stableKey !in existingKeys }
                    current.copy(
                        cards = current.cards + appended,
                        appending = false,
                    )
                },
                onFailure = { e ->
                    Log.w(TAG, "loadMore($section) failed: ${e.message}")
                    current.copy(appending = false)
                },
            )
        }
    }

    fun markCardClicked(card: HomeCard) {
        lastFocusedKey[_selectedSection.value] = card.stableKey
        _pendingGridFocus.value = true
    }
    fun consumePendingGridFocus() { _pendingGridFocus.value = false }

    /** ContentPane 重新挂载时读：要把焦点钉回哪张卡。null = 走 grid 默认首项。 */
    fun focusKeyFor(section: SectionId): String? = lastFocusedKey[section]

    private fun stateFor(section: SectionId): MutableStateFlow<SectionState> =
        states.getOrPut(section) { MutableStateFlow(SectionState.Idle) }

    private fun load(section: SectionId) {
        val flow = stateFor(section)

        // PLACEHOLDER 不发请求，直接占位提示
        if (section.kind == SectionId.Kind.PLACEHOLDER) {
            flow.value = SectionState.Empty(
                hint = when (section) {
                    SectionId.FAVORITE -> "请到「个人中心 → 我的收藏」查看"
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
    }
}
