package dev.tvbili.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.tvbili.data.repo.HomeCard

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToLive: (Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val selected by vm.selectedSection.collectAsStateWithLifecycle()
    val state by vm.stateOf(selected).collectAsStateWithLifecycle()
    val pendingGridFocus by vm.pendingGridFocus.collectAsStateWithLifecycle()
    val resolvingPgcKey by vm.resolvingPgcKey.collectAsStateWithLifecycle()

    // 点 PGC 综艺卡 → VM 异步解析 season → 发 bvid 事件 → 这里转 nav
    LaunchedEffect(Unit) {
        vm.pgcNavigateEvent.collect { bvid -> onNavigateToVideo(bvid) }
    }

    Row(modifier = modifier.fillMaxSize()) {
        SideBar(
            sections = sections,
            selected = selected,
            onSelect = vm::selectSection,
            onAvatarClick = onNavigateToProfile,
            onSearchClick = onNavigateToSearch,
            onSettingsClick = onNavigateToSettings,
            suppressAutoFocus = pendingGridFocus,
        )
        ContentPane(
            section = selected,
            state = state,
            onCardClick = { card ->
                when (card) {
                    is HomeCard.Video -> {
                        vm.markCardClicked(card)
                        onNavigateToVideo(card.bvid)
                    }
                    is HomeCard.Live -> {
                        vm.markCardClicked(card)
                        onNavigateToLive(card.roomId)
                    }
                    is HomeCard.PgcSeason -> vm.openPgcSeason(card)
                }
            },
            onRetry = { vm.retry(selected) },
            pendingGridFocus = pendingGridFocus,
            onConsumeGridFocus = vm::consumePendingGridFocus,
            lastFocusedKey = vm.focusKeyFor(selected),
            onLoadMore = { vm.loadMore(selected) },
            resolvingPgcKey = resolvingPgcKey,
        )
    }
}
