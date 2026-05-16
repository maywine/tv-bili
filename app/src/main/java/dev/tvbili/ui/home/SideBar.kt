package dev.tvbili.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvbili.tv.LocalIsTvDevice
import dev.tvbili.tv.tvFocusable

@Composable
fun SideBar(
    sections: List<SectionId>,
    selected: SectionId,
    onSelect: (SectionId) -> Unit,
    onAvatarClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 上一帧用户点了卡片去看详情，正在等焦点回到 grid。SideBar 要让出本次自动抢焦。
     * 默认 false——正常首次进入 / 从设置返回时 SideBar 仍然抢初始焦。
     */
    suppressAutoFocus: Boolean = false,
) {
    val isTv = LocalIsTvDevice.current
    // 焦点定位到「当前选中分区」而不是第一项——否则从视频/直播返回时焦点重落
    // 在首项，叠加 onFocusChanged 驱动的自动选区会把分区悄悄重置成第一项。
    val selectedItemRequester = remember { FocusRequester() }
    val hasSections = sections.isNotEmpty()

    // 用 Unit 作 key：只在 SideBar 进合成时执行一次；之后 suppressAutoFocus 被消费翻 false
    // 也不会让 SideBar 反过来再抢焦。
    LaunchedEffect(Unit) {
        if (isTv && hasSections && !suppressAutoFocus) {
            try {
                selectedItemRequester.requestFocus()
            } catch (_: Throwable) {
                // 首次合成尚未挂上 layout，忽略；用户按 ↓ 自然进入
            }
        }
    }

    Column(
        modifier = modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(Color(0xFF0A0A0A))
            .then(if (isTv) Modifier.focusGroup() else Modifier)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 顶部：头像（占位）+ 搜索入口
        SideBarIconItem(label = "我", onClick = onAvatarClick)
        SideBarIconItem(label = "🔍", onClick = onSearchClick)

        Spacer(Modifier.height(16.dp))

        // 分区列表：首项挂 focusRequester；空列表显示提示，引导用户去设置添加分区
        if (sections.isEmpty()) {
            Text(
                text = "请到\n设置\n添加\n分区",
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            sections.forEach { sec ->
                SideBarSectionItem(
                    label = sec.label,
                    selected = (sec == selected),
                    onClick = { onSelect(sec) },
                    modifier = if (sec == selected) Modifier.focusRequester(selectedItemRequester) else Modifier,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 底部：设置
        SideBarIconItem(label = "设置", onClick = onSettingsClick)
    }
}

@Composable
private fun SideBarSectionItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TV 模式下焦点变化即触发选区（D-pad 上下导航就能切换右侧内容，不再要求按 OK）；
    // 触屏 / 鼠标走 clickable。HomeViewModel.selectSection 自身做了 stale 判断，
    // 重复触发同一分区是 no-op。
    Box(
        modifier = modifier
            .width(80.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent,
            )
            .tvFocusable(
                cornerRadius = 28.dp,
                scaleOnFocus = 1.08f,
                onFocusChanged = { focused -> if (focused) onClick() },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0),
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SideBarIconItem(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .tvFocusable(cornerRadius = 28.dp, scaleOnFocus = 1.08f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color(0xFFE0E0E0),
            fontSize = 12.sp,
        )
    }
}
