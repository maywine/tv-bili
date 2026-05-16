package dev.tvbili.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvbili.ui.home.components.formatDuration

/**
 * 简化进度条 —— 仅时间 + 进度，无 chip / 焦点 / 控制项。
 *
 * 触发：用户按 OK / seek 时短暂显示；与 [VideoOverlay] 的完整控制菜单**互斥**——
 * 完整菜单显时不渲此条，避免叠加。
 *
 * 不挂 focusable / clickable：D-pad 按键全部透给根 Box 的 onPreviewKeyEvent。
 */
@Composable
fun MiniProgressBar(
    currentMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xB3000000)) // 70% 黑——比完整菜单淡，视觉更轻
            .padding(horizontal = 32.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color(0xFF303030),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration((currentMs / 1000).toInt()),
                color = Color.White,
                fontSize = 12.sp,
            )
            Text(
                text = formatDuration((durationMs / 1000).toInt()),
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp,
            )
        }
    }
}
