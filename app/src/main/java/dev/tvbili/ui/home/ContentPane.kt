package dev.tvbili.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
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
) {
    val isTv = LocalIsTvDevice.current
    val gridState = rememberLazyGridState()
    val gridFocusRequester = remember { FocusRequester() }

    // 从详情页返回 → 主动把焦点请回 grid 容器，focusRestorer 自然接管到上次卡片。
    // pendingFocus 仅当为 true 时启动一次焦点请求，随后立刻消费成 false 防止再次抢焦。
    LaunchedEffect(pendingFocus, cards.size) {
        if (isTv && pendingFocus && cards.isNotEmpty()) {
            try { gridFocusRequester.requestFocus() } catch (_: Throwable) {}
            onConsumeFocus()
        }
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
                }
            },
        ) { card ->
            when (card) {
                is HomeCard.Video -> VideoCard(card = card, onClick = { onCardClick(card) })
                is HomeCard.Live -> LiveRoomCard(card = card, onClick = { onCardClick(card) })
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
