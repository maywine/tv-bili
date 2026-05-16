package dev.tvbili.ui.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tvbili.data.model.FavFolder
import dev.tvbili.tv.tvFocusable
import dev.tvbili.ui.home.components.VideoCard

/**
 * 我的收藏入口。
 *
 * 两层结构：
 * - 一级：收藏夹列表（[FavoriteState.Folders]）
 * - 二级：选中收藏夹下的视频卡片网格（[FavoriteFolderState.Ready]）
 *
 * 通过 [FavoriteFolderState] 是否为 [FavoriteFolderState.Idle] 判定当前在哪一层。
 * Back 键：在二级 → 回一级；在一级 → 调用 [onBack] 回上级。
 */
@Composable
fun FavoriteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: FavoriteViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val folderState by vm.folderState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadFolders() }

    val inFolder = folderState !is FavoriteFolderState.Idle
    BackHandler(enabled = inFolder) { vm.clearFolder() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (inFolder) {
            FolderDetail(
                folderState = folderState,
                onCardClick = onNavigateToVideo,
                onLoadMore = vm::loadMoreInFolder,
            )
        } else {
            FolderList(
                state = state,
                onFolderClick = vm::openFolder,
                onRetry = vm::loadFolders,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun FolderList(
    state: FavoriteState,
    onFolderClick: (FavFolder) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = "我的收藏",
        color = Color.White,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )

    when (state) {
        FavoriteState.Idle, FavoriteState.Loading -> CenterBox {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is FavoriteState.Error -> CenterBox {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("加载失败", color = Color.White, fontSize = 16.sp)
                Text(state.message, color = Color(0xFFB0B0B0), fontSize = 13.sp)
                ActionRow(
                    primaryLabel = "重试",
                    onPrimary = onRetry,
                    secondaryLabel = "返回",
                    onSecondary = onBack,
                )
            }
        }
        is FavoriteState.Folders -> {
            if (state.folders.isEmpty()) {
                CenterBox {
                    Text("还没有任何收藏夹", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.folders, key = { it.id }) { folder ->
                        FolderRow(folder = folder, onClick = { onFolderClick(folder) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FavFolder, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.02f)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column {
            Text(folder.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "${folder.mediaCount} 个视频",
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun FolderDetail(
    folderState: FavoriteFolderState,
    onCardClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    when (folderState) {
        FavoriteFolderState.Idle, FavoriteFolderState.Loading -> CenterBox {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is FavoriteFolderState.Error -> CenterBox {
            Text("加载失败：${folderState.message}", color = Color(0xFFB0B0B0), fontSize = 14.sp)
        }
        is FavoriteFolderState.Ready -> {
            Text(
                text = folderState.folderTitle,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (folderState.cards.isEmpty()) {
                CenterBox {
                    Text("这个收藏夹是空的", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                }
            } else {
                FolderVideoGrid(
                    ready = folderState,
                    onCardClick = onCardClick,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun FolderVideoGrid(
    ready: FavoriteFolderState.Ready,
    onCardClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) false
            else (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= total - 8
        }
    }
    LaunchedEffect(shouldLoadMore, ready.appending, ready.cards.size) {
        if (shouldLoadMore && !ready.appending && ready.hasMore) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(ready.cards, key = { it.bvid }) { card ->
            VideoCard(card = card, onClick = { onCardClick(card.bvid) })
        }
        if (ready.appending) {
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
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary)
                .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.06f)
                .clickable(onClick = onPrimary)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text(primaryLabel, color = Color.White, fontSize = 14.sp) }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A))
                .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.06f)
                .clickable(onClick = onSecondary)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text(secondaryLabel, color = Color.White, fontSize = 14.sp) }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}
