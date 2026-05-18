package dev.tvbili.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.repo.HistoryRepository
import dev.tvbili.data.repo.HomeCard
import dev.tvbili.data.repo.HomeRepository
import dev.tvbili.data.repo.PgcRepository
import dev.tvbili.data.store.SectionConfigStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HomeRepository()
    private val historyRepo = HistoryRepository(application)
    private val pgcRepo = PgcRepository()

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

    /**
     * 当前直播筛选的分区参数。0 / 0 = 全站（不带分区参数走 getRoomList）。
     * 一级 / 二级语义见 [dev.tvbili.ui.home.components.LiveAreaWhitelist]。
     */
    private val _selectedLiveParentId = MutableStateFlow(0)
    val selectedLiveParentId: StateFlow<Int> = _selectedLiveParentId.asStateFlow()

    private val _selectedLiveAreaId = MutableStateFlow(0)
    val selectedLiveAreaId: StateFlow<Int> = _selectedLiveAreaId.asStateFlow()

    /**
     * PGC 分区已加载到第几页。`load()` 重置为 1；`loadMore()` 成功一次 +1。
     * 各 PGC 分区独立计数——CINEMA 翻到第 3 页时切到 VARIETY 不影响后者从 1 开始。
     */
    private val pgcPageMap = ConcurrentHashMap<SectionId, Int>()

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

    /**
     * 用户在直播分区栏切了某个 chip。
     * - 同 (parent, area) 再选：短路（chip 焦点扫过同一项不会重发请求）。
     * - 切到新值：强制重新 load(LIVE)，旧 cards 跟新筛选条件不匹配，TTL 不再适用。
     */
    fun selectLiveArea(parentId: Int, areaId: Int) {
        if (_selectedLiveParentId.value == parentId && _selectedLiveAreaId.value == areaId) return
        _selectedLiveParentId.value = parentId
        _selectedLiveAreaId.value = areaId
        load(SectionId.LIVE)
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
        SectionId.Kind.PGC -> 10L * 60 * 1000 // 综艺/番剧更新频率低，10 min 够新鲜
        SectionId.Kind.HISTORY -> 0L
        SectionId.Kind.PLACEHOLDER -> Long.MAX_VALUE
    }

    fun retry(section: SectionId) = load(section)

    /**
     * 分页追加：滚到 grid 末尾时由 ContentPane 触发。
     * 支持的分区：
     * - RECOMMEND：用 rcmdFreshIdx 顺序拉推荐流
     * - PGC：用 pgcPageMap 拉 pgc/season/index/result 下一页
     * 其它分区 no-op（B 站 ranking/popular 不暴露稳定的分页接口，硬分页易拿重复）。
     *
     * 共通行为：
     * - 仅在 Loaded 且非 appending 时启动；并发 selectSection 切走时按 cur 视图引用守恒，
     *   旧追加结果不会污染新分区。
     * - 失败：保持原 cards 不变，把 appending 翻回 false（不弹 UI 错误，下一次滚动重试）。
     * - 按 stableKey 去重，防服务端重叠返回。
     */
    fun loadMore(section: SectionId) {
        when (section.kind) {
            SectionId.Kind.RECOMMEND -> loadMoreRecommend(section)
            SectionId.Kind.PGC -> loadMorePgc(section)
            else -> Unit
        }
    }

    private fun loadMoreRecommend(section: SectionId) {
        val flow = stateFor(section)
        val cur = flow.value as? SectionState.Loaded ?: return
        if (cur.appending) return
        flow.value = cur.copy(appending = true)
        viewModelScope.launch {
            val freshIdx = rcmdFreshIdx++
            val result = repo.loadRecommend(freshIdx)
            appendResult(flow, result, tag = "loadMoreRecommend")
        }
    }

    private fun loadMorePgc(section: SectionId) {
        val flow = stateFor(section)
        val cur = flow.value as? SectionState.Loaded ?: return
        if (cur.appending) return
        // 空列表说明 index/result 不可用、当前在 rank 兜底页——rank 不分页，跳过
        if (cur.cards.isEmpty()) return
        flow.value = cur.copy(appending = true)
        viewModelScope.launch {
            val nextPage = (pgcPageMap[section] ?: 1) + 1
            val result = pgcRepo.loadSeasonIndex(seasonType = section.rid, page = nextPage)
            val appended = appendResult(flow, result, tag = "loadMorePgc[page=$nextPage]")
            if (appended) pgcPageMap[section] = nextPage
        }
    }

    /**
     * 把分页 result 合并进 flow.value（必须是 Loaded）。返回是否真正追加了 ≥ 1 条新内容
     * ——调用方据此决定是否把 page 计数 +1（避免空响应也推进游标，下次还是空）。
     */
    private fun <T : HomeCard> appendResult(
        flow: MutableStateFlow<SectionState>,
        result: Result<List<T>>,
        tag: String,
    ): Boolean {
        val current = flow.value as? SectionState.Loaded ?: return false
        return result.fold(
            onSuccess = { more ->
                val existingKeys = current.cards.mapTo(mutableSetOf()) { it.stableKey }
                val appended = more.filter { it.stableKey !in existingKeys }
                flow.value = current.copy(
                    cards = current.cards + appended,
                    appending = false,
                )
                appended.isNotEmpty()
            },
            onFailure = { e ->
                Log.w(TAG, "$tag failed: ${e.message}")
                flow.value = current.copy(appending = false)
                false
            },
        )
    }

    fun markCardClicked(card: HomeCard) {
        lastFocusedKey[_selectedSection.value] = card.stableKey
        _pendingGridFocus.value = true
    }
    fun consumePendingGridFocus() { _pendingGridFocus.value = false }

    /**
     * PGC season → bvid 解析中标志位；UI 用它画 loading 蒙层避免用户连点。
     * stableKey = HomeCard.stableKey 形态（"pgc_${seasonId}"），仅一条进行中。
     */
    private val _resolvingPgcKey = MutableStateFlow<String?>(null)
    val resolvingPgcKey: StateFlow<String?> = _resolvingPgcKey.asStateFlow()

    /**
     * PGC 卡解析完发射的 bvid 事件。MainActivity 收到后 `screen = AppScreen.Video(bvid)`。
     * 用 SharedFlow（replay=0）避免重组重复消费；extraBufferCapacity=1 防止快速点击丢事件。
     */
    private val _pgcNavigateEvent = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val pgcNavigateEvent: SharedFlow<String> = _pgcNavigateEvent.asSharedFlow()

    /**
     * 点击 PGC 季度卡：拉 season 详情 → 取最新一集 bvid → 发 navigate 事件。
     * 失败时仅记日志（UI 自动消除 loading），不阻断用户重试。
     */
    fun openPgcSeason(card: HomeCard.PgcSeason) {
        if (_resolvingPgcKey.value == card.stableKey) return // 防连点
        _resolvingPgcKey.value = card.stableKey
        viewModelScope.launch {
            pgcRepo.resolveLatestEpisodeBvid(card.seasonId).fold(
                onSuccess = { bvid ->
                    lastFocusedKey[_selectedSection.value] = card.stableKey
                    _pendingGridFocus.value = true
                    _pgcNavigateEvent.tryEmit(bvid)
                },
                onFailure = { e ->
                    Log.w(TAG, "resolvePgcSeason(${card.seasonId}) failed: ${e.message}")
                },
            )
            _resolvingPgcKey.value = null
        }
    }

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
        if (section.kind == SectionId.Kind.PGC) pgcPageMap[section] = 1
        viewModelScope.launch {
            val result = when (section.kind) {
                SectionId.Kind.RECOMMEND ->
                    repo.loadRecommend(rcmdFreshIdx).also { rcmdFreshIdx++ }
                SectionId.Kind.POPULAR -> repo.loadPopular(page = 1)
                SectionId.Kind.LIVE -> repo.loadLive(
                    parentAreaId = _selectedLiveParentId.value,
                    areaId = _selectedLiveAreaId.value,
                    page = 1,
                )
                SectionId.Kind.RANKING -> repo.loadRanking(rid = section.rid)
                SectionId.Kind.PGC ->
                    // PGC 分区把 rid 字段重用为 season_type
                    pgcRepo.loadSeasonIndex(seasonType = section.rid, page = 1)
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
