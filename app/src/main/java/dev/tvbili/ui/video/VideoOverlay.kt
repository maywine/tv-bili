package dev.tvbili.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvbili.tv.tvFocusable
import dev.tvbili.ui.home.components.formatDuration

@Composable
fun VideoOverlay(
    title: String,
    uploader: String,
    currentMs: Long,
    durationMs: Long,
    acceptQuality: List<Int>,
    acceptDescription: List<String>,
    selectedQn: Int,
    onSelectQn: (Int) -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    speed: Float,
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 控件浮层弹出时由父屏调用 [FocusRequester.requestFocus] 把焦点抓到第一颗 chip，
     * 让 D-pad 立刻能横向移动而不是停在根 Box。null = 调用方不接管焦点。
     */
    entryFocusRequester: FocusRequester? = null,
    /**
     * 用户按向上键时调用——「Up 收菜单」语义。挂在 Overlay 的 Column 上而不是父屏根 Box，
     * 因为 AnimatedVisibility 创建独立子合成 / 焦点边界，父屏根 Box 的 onPreviewKeyEvent
     * 触达不到内部 chip 的焦点路径；放本层 Column 上则与焦点同级，preview 一定能截到。
     */
    onDismissUp: () -> Unit = {},
) {
    val progress = if (durationMs > 0) currentMs.toFloat() / durationMs else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC000000)) // 80% 黑
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                    onDismissUp(); true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(text = uploader, color = Color(0xFFB0B0B0), fontSize = 12.sp)

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color(0xFF303030),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatDuration((currentMs / 1000).toInt())} / ${formatDuration((durationMs / 1000).toInt())}",
                color = Color.White,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityChips(
                    acceptQuality = acceptQuality,
                    acceptDescription = acceptDescription,
                    selectedQn = selectedQn,
                    onSelectQn = onSelectQn,
                    entryFocusRequester = entryFocusRequester,
                )
                ToggleChip(
                    label = if (danmakuEnabled) "弹幕✓" else "弹幕",
                    selected = danmakuEnabled,
                    onClick = onToggleDanmaku,
                )
                SpeedChips(speed = speed, onSelectSpeed = onSelectSpeed)
            }
        }
    }
}

@Composable
private fun QualityChips(
    acceptQuality: List<Int>,
    acceptDescription: List<String>,
    selectedQn: Int,
    onSelectQn: (Int) -> Unit,
    entryFocusRequester: FocusRequester?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        acceptQuality.zip(acceptDescription).take(4).forEachIndexed { index, (qn, desc) ->
            ToggleChip(
                label = desc,
                selected = qn == selectedQn,
                onClick = { onSelectQn(qn) },
                // 把入口焦点挂在第一颗清晰度 chip 上——overlay 一弹出，D-pad 就在这里
                modifier = if (index == 0 && entryFocusRequester != null) {
                    Modifier.focusRequester(entryFocusRequester)
                } else Modifier,
            )
        }
    }
}

@Composable
private fun SpeedChips(speed: Float, onSelectSpeed: (Float) -> Unit) {
    val options = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { s ->
            ToggleChip(
                label = if (s == 1f) "1x" else "${s}x",
                selected = s == speed,
                onClick = { onSelectSpeed(s) },
            )
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A)
    val fg = if (selected) Color.White else Color(0xFFE0E0E0)
    Box(
        // 调用方传入的 modifier 优先（含 focusRequester）；放在最前以便 focusRequester
        // 能挂到 clickable 提供的 focusable 节点上。
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .tvFocusable(cornerRadius = 14.dp, scaleOnFocus = 1.06f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 12.sp)
    }
}
