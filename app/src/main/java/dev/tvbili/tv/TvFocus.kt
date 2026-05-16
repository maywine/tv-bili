package dev.tvbili.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TV 通用焦点修饰符 —— 只负责视觉反馈（缩放 + 边框 + onFocusChanged），
 * **不再自己挂 `.focusable()`**，避免与后续 `.clickable()` 内部自带的
 * focusable 形成双层焦点节点（双层会导致 D-pad 按两下：第一次把焦点从
 * 外层推到内层，第二次才触发 onClick）。
 *
 * 用法：始终配合 `.clickable()` 在后面接，clickable 自带 focusable，
 * 焦点状态会向上传播给本修饰符里的 `onFocusChanged`。
 *
 * 设计原则：
 * - 手机端零开销：`if (!isTv) return this` 直接 return，无分配/无重组
 * - TV 端：纯视觉，不抢焦点目标
 *
 * 如果某个组件没有 clickable（如纯展示卡片需要 hover 高亮），改用
 * [tvFocusableNoVisuals] 或显式 `.focusable()`。
 */
@Composable
fun Modifier.tvFocusable(
    enabled: Boolean = true,
    scaleOnFocus: Float = 1.05f,
    borderWidth: Dp = 3.dp,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 12.dp,
    onFocusChanged: (Boolean) -> Unit = {},
): Modifier {
    val isTv = LocalIsTvDevice.current
    if (!isTv || !enabled) return this

    return this.composed {
        var focused by remember { mutableStateOf(false) }
        val targetScale by animateFloatAsState(
            targetValue = if (focused) scaleOnFocus else 1f,
            label = "tv-focus-scale",
        )

        Modifier
            .scale(targetScale)
            .border(
                width = if (focused) borderWidth else 0.dp,
                color = if (focused) borderColor else Color.Transparent,
                shape = RoundedCornerShape(cornerRadius),
            )
            .onFocusChanged {
                if (focused != it.isFocused) {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
            }
    }
}

/**
 * 仅 TV 模式下生效的 [Modifier.focusable] 简版。
 * 不带视觉反馈，适合自身已绘制焦点态的组件。
 */
@Composable
fun Modifier.tvFocusableNoVisuals(enabled: Boolean = true): Modifier {
    val isTv = LocalIsTvDevice.current
    if (!isTv) return this
    return this.focusable(enabled = enabled)
}
