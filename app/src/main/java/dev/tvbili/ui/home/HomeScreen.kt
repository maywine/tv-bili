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
import dev.tvbili.data.repo.PgcOrder
import dev.tvbili.data.repo.SearchScope
import dev.tvbili.data.model.PgcPlayback
import dev.tvbili.ui.home.components.LiveAreaChipRow
import dev.tvbili.ui.home.components.PgcCatalogHeader

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onNavigateToPgc: (PgcPlayback) -> Unit,
    onNavigateToLive: (Long) -> Unit,
    onNavigateToSearch: (SearchScope) -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
) {
    val sections by vm.sections.collectAsStateWithLifecycle()
    val selected by vm.selectedSection.collectAsStateWithLifecycle()
    val state by vm.stateOf(selected).collectAsStateWithLifecycle()
    val pendingGridFocus by vm.pendingGridFocus.collectAsStateWithLifecycle()
    val resolvingPgcKey by vm.resolvingPgcKey.collectAsStateWithLifecycle()
    val selectedLiveParentId by vm.selectedLiveParentId.collectAsStateWithLifecycle()
    val selectedLiveAreaId by vm.selectedLiveAreaId.collectAsStateWithLifecycle()
    val pgcOrders by vm.pgcOrders.collectAsStateWithLifecycle()

    // 节目卡由 VM 解析集数信息后导航，保留电视播放所需的 ep_id
    LaunchedEffect(Unit) {
        vm.pgcNavigateEvent.collect { onNavigateToPgc(it) }
    }

    Row(modifier = modifier.fillMaxSize()) {
        SideBar(
            sections = sections,
            selected = selected,
            onSelect = vm::selectSection,
            onAvatarClick = onNavigateToProfile,
            onSearchClick = { onNavigateToSearch(SearchScope.VIDEO) },
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
                        if (card.pgc != null) onNavigateToPgc(card.pgc) else onNavigateToVideo(card.bvid)
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
            header = if (selected.kind == SectionId.Kind.LIVE) {
                {
                    LiveAreaChipRow(
                        selectedParentId = selectedLiveParentId,
                        selectedAreaId = selectedLiveAreaId,
                        onSelect = vm::selectLiveArea,
                    )
                }
            } else if (selected == SectionId.CINEMA || selected == SectionId.VARIETY) {
                {
                    PgcCatalogHeader(
                        label = selected.label,
                        order = pgcOrders[selected] ?: PgcOrder.RECOMMENDED,
                        onOrder = { vm.selectPgcOrder(selected, it) },
                        onSearch = {
                            onNavigateToSearch(if (selected == SectionId.CINEMA) SearchScope.CINEMA else SearchScope.VARIETY)
                        },
                    )
                }
            } else {
                null
            },
        )
    }
}
