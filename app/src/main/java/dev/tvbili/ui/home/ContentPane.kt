package dev.tvbili.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvbili.data.repo.HomeCard
import dev.tvbili.tv.LocalIsTvDevice
import dev.tvbili.tv.tvFocusable
import dev.tvbili.ui.home.components.LiveRoomCard
import dev.tvbili.ui.home.components.PgcSeasonCard
import dev.tvbili.ui.home.components.VideoCard

@Composable
fun ContentPane(
    section: SectionId,
    state: SectionState,
    onCardClick: (HomeCard) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** 从详情页返回时为 true：grid 应当主动 requestFocus → focusRestorer 还原到上次卡片 */
    pendingGridFocus: Boolean = false,
    onConsumeGridFocus: () -> Unit = {},
    /** 上次用户点击的卡片 stableKey；用于跨 HomeScreen 重建准确还原焦点。 */
    lastFocusedKey: String? = null,
    /** 滑到列表尾部时调用：仅 [SectionId.Kind.RECOMMEND] 在 ViewModel 内追加分页。 */
    onLoadMore: () -> Unit = {},
    /** 当前正在解析 season → bvid 的 PGC 卡 stableKey；其它卡正常渲染。 */
    resolvingPgcKey: String? = null,
    /**
     * 分区上方插槽（如直播分区 Chip 栏）。null = 不显示；非空时整面板改为 Column 布局，
     * header 永远可见，下方按 [state] 切 grid / loading / error / empty。
     */
    header: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header != null) header()
        Box(modifier = Modifier.fillMaxSize()) {
            when (state) {
                SectionState.Idle, SectionState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is SectionState.Error -> {
                    ErrorView(
                        message = state.message,
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is SectionState.Loaded -> {
                    if (state.cards.isEmpty()) {
                        Text(
                            text = "空空如也",
                            color = Color.LightGray,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        CardGrid(
                            section = section,
                            cards = state.cards,
                            onCardClick = onCardClick,
                            pendingFocus = pendingGridFocus,
                            onConsumeFocus = onConsumeGridFocus,
                            lastFocusedKey = lastFocusedKey,
                            onLoadMore = onLoadMore,
                            appending = state.appending,
                            resolvingPgcKey = resolvingPgcKey,
                        )
                    }
                }

                is SectionState.Empty -> {
                    EmptyView(
                        hint = state.hint,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyView(
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🕓", fontSize = 48.sp)
        Text(hint, color = Color(0xFFB0B0B0), fontSize = 14.sp)
    }
}

@Composable
private fun CardGrid(
    section: SectionId,
    cards: List<HomeCard>,
    onCardClick: (HomeCard) -> Unit,
    pendingFocus: Boolean,
    onConsumeFocus: () -> Unit,
    lastFocusedKey: String?,
    onLoadMore: () -> Unit,
    appending: Boolean,
    resolvingPgcKey: String?,
) {
    val isTv = LocalIsTvDevice.current
    val gridState = rememberLazyGridState()
    val gridFocusRequester = remember { FocusRequester() }
    val targetFocusRequester = remember { FocusRequester() }

    val targetIndex = remember(cards, lastFocusedKey) {
        if (lastFocusedKey == null) -1 else cards.indexOfFirst { it.stableKey == lastFocusedKey }
    }

    // 从详情页返回 → 优先：定位到上次焦点卡 + scrollToItem + requestFocus 到该卡。
    // 没有 lastFocusedKey（或卡片被刷新换掉了）→ fallback 到 grid 容器 + focusRestorer。
    // pendingFocus 仅 true 时启动一次焦点请求，立刻消费成 false 防止重复抢焦。
    LaunchedEffect(pendingFocus, cards.size, targetIndex) {
        if (!isTv || !pendingFocus || cards.isEmpty()) return@LaunchedEffect
        if (targetIndex >= 0) {
            runCatching { gridState.scrollToItem(targetIndex) }
            // grid attach 完一帧后再请焦，避免 layout 还没就绪
            kotlinx.coroutines.delay(16)
            runCatching { targetFocusRequester.requestFocus() }
        } else {
            runCatching { gridFocusRequester.requestFocus() }
        }
        onConsumeFocus()
    }

    // 滚到末尾自动加载更多（仅 RECOMMEND 在 VM 内会真正追加；其他分区 no-op）
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) false
            else {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                // 距离末尾 ≤ 8 项时预拉下一页
                last >= total - 8
            }
        }
    }
    LaunchedEffect(shouldLoadMore, appending, cards.size) {
        if (shouldLoadMore && !appending && cards.isNotEmpty()) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isTv) Modifier
                    .focusRequester(gridFocusRequester)
                    .focusRestorer()
                else Modifier,
            ),
    ) {
        items(
            items = cards,
            key = { it.stableKey },
            contentType = {
                when (it) {
                    is HomeCard.Video -> "video_card"
                    is HomeCard.Live -> "live_card"
                    is HomeCard.PgcSeason -> "pgc_card"
                }
            },
        ) { card ->
            val itemModifier = if (isTv && card.stableKey == lastFocusedKey) {
                Modifier.focusRequester(targetFocusRequester)
            } else {
                Modifier
            }
            when (card) {
                is HomeCard.Video -> VideoCard(
                    card = card,
                    onClick = { onCardClick(card) },
                    modifier = itemModifier,
                )
                is HomeCard.Live -> LiveRoomCard(
                    card = card,
                    onClick = { onCardClick(card) },
                    modifier = itemModifier,
                )
                is HomeCard.PgcSeason -> PgcSeasonCard(
                    card = card,
                    onClick = { onCardClick(card) },
                    resolving = (card.stableKey == resolvingPgcKey),
                    modifier = itemModifier,
                )
            }
        }
        if (appending) {
            item(
                span = { GridItemSpan(maxLineSpan) },
                contentType = "loading_footer",
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("加载失败", color = Color.White, fontSize = 18.sp)
        Text(message, color = Color(0xFFB0B0B0), fontSize = 14.sp)
        Box(
            modifier = Modifier
                .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.08f)
                .clickable(onClick = onRetry)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("重试", color = Color.White, fontSize = 14.sp)
        }
    }
}
