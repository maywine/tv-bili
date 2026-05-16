package dev.tvbili.ui.history

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvbili.data.repo.HistoryRepository
import dev.tvbili.data.repo.HomeCard
import dev.tvbili.ui.home.components.VideoCard

/**
 * 个人中心 → 历史记录入口（独立屏幕，区别于首页可选 HISTORY 分区）。
 *
 * 直接读 [HistoryRepository.getRecent]，无 VM——历史本来就是本地 DataStore，O(1)。
 * 进屏一次性加载；返回后下次进入再加载，不缓存（数据小，反复读无压力）。
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember(context) { HistoryRepository(context.applicationContext as Application) }
    var cards by remember { mutableStateOf<List<HomeCard>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cards = runCatching { repo.getRecent() }.getOrDefault(emptyList())
        loaded = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "历史记录",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )

        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            cards.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有播放历史", color = Color(0xFFB0B0B0), fontSize = 14.sp)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cards, key = { it.stableKey }) { card ->
                    when (card) {
                        is HomeCard.Video -> VideoCard(
                            card = card,
                            onClick = { onNavigateToVideo(card.bvid) },
                        )
                        // 历史记录只可能是 UGC 视频；其余变体仅为穷尽 when，运行时不会命中
                        is HomeCard.Live, is HomeCard.PgcSeason -> Unit
                    }
                }
            }
        }
    }
}
