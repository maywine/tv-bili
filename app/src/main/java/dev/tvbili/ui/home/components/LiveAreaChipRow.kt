package dev.tvbili.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvbili.tv.LocalIsTvDevice
import dev.tvbili.tv.tvFocusable

/**
 * 直播分区筛选项白名单 —— 写死 4 个常用入口，不再从接口拉全量分区树。
 *
 * 字段对应 `getRoomList` 的两个分区参数：
 * - [parentAreaId]：一级分区 id（0 = 全站，2 = 网游，6 = 单机游戏 等）
 * - [areaId]：二级分区 id（0 = 不限子分区，仅按 parent 筛）
 *
 * 想加新 chip 时往 [LiveAreaWhitelist] 里追加即可；ID 来自 `/room/v1/Area/getList`。
 */
data class LiveAreaChip(
    val label: String,
    val parentAreaId: Int,
    val areaId: Int,
)

/** key 用 "parent_area" 组合，避免「全站」和「整个网游」这种 areaId=0 的项撞 key。 */
private fun LiveAreaChip.key(): String = "${parentAreaId}_${areaId}"

/**
 * UI 白名单。顺序就是显示顺序。
 *
 * - 全部：parent=0, area=0，不带任何分区参数走 getRoomList
 * - 英雄联盟：网游/英雄联盟（实测 area_id=86）
 * - DOTA2：网游/DOTA2（area_id=92）
 * - 单机游戏：整个「单机游戏」一级分区（parent=6, area=0），不限子分区
 */
val LiveAreaWhitelist: List<LiveAreaChip> = listOf(
    LiveAreaChip("全部", 0, 0),
    LiveAreaChip("英雄联盟", 2, 86),
    LiveAreaChip("DOTA2", 2, 92),
    LiveAreaChip("单机游戏", 6, 0),
)

/**
 * 直播分区切换栏 —— 单行 chip。TV 端「焦点 = 选中」，免按确定键即可筛。
 *
 * ViewModel 的 `selectLiveArea` 对同一 (parent, area) 短路，扫焦不会重发请求。
 */
@Composable
fun LiveAreaChipRow(
    selectedParentId: Int,
    selectedAreaId: Int,
    onSelect: (parentId: Int, areaId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = LiveAreaWhitelist, key = { it.key() }) { chip ->
            AreaChip(
                label = chip.label,
                selected = selectedParentId == chip.parentAreaId &&
                    selectedAreaId == chip.areaId,
                onSelect = { onSelect(chip.parentAreaId, chip.areaId) },
            )
        }
    }
}

@Composable
private fun AreaChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val isTv = LocalIsTvDevice.current
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF242424)
    val fg = if (selected) Color.White else Color(0xFFE0E0E0)
    Box(
        modifier = Modifier
            .tvFocusable(
                cornerRadius = 18.dp,
                scaleOnFocus = 1.06f,
                onFocusChanged = { focused ->
                    // TV 上焦点经过即筛——遥控器顺手习惯。VM 内对同 (parent, area) 短路，
                    // 反复扫焦不会刷请求；focused=false 时不做任何事。
                    if (focused && isTv) onSelect()
                },
            )
            .clickable(onClick = onSelect)
            .background(bg, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = fg, fontSize = 14.sp)
    }
}
