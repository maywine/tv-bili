package dev.tvbili.ui.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.tvbili.player.DanmakuConfig
import dev.tvbili.player.DanmakuLoader
import kotlinx.coroutines.delay
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.ui.widget.DanmakuView

/**
 * AndroidView 包装 DanmakuView。
 *
 * **关键约束**：DanmakuFlameMaster 的 [DanmakuView.seekTo] 语义是「跳到 t 并按 t 窗口
 * 重新构建弹幕队列」——它会清空当前正在飘出的弹幕。所以**不可**用「每秒 seekTo 到
 * player 当前位置」做同步——那等于每秒清空一次队列，表现就是弹幕开了一会儿越来越少
 * 最后完全没有。
 *
 * 同步策略：
 * 1. **进合成首次对齐**：等 prepared 完成后做一次 [DanmakuView.seekTo] 把弹幕时间轴
 *    对齐 player.currentPosition（覆盖中途打开弹幕 / 从历史进度续播的场景）
 * 2. **pause/resume 跟随**：500ms 轮询 player.isPlaying 控制 pause/resume——这两个
 *    操作不动弹幕队列
 * 3. **player seek 时再次对齐**：通过 [Player.Listener.onPositionDiscontinuity] 监听
 *    seek 事件（用户拖动 / 切清晰度 / 跳跃），单次 seekTo 重新对齐
 *
 * 平时 DanmakuView 的内部 timer 自行流动；与 player 的细微漂移（< 1s）肉眼不可察。
 */
@UnstableApi
@Composable
fun DanmakuSurface(
    player: ExoPlayer,
    xmlBytes: ByteArray?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var danmakuView by remember { mutableStateOf<DanmakuView?>(null) }
    val context = remember { DanmakuConfig.create() }
    val hasData = xmlBytes != null && xmlBytes.isNotEmpty()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            DanmakuView(ctx).apply {
                setCallback(object : DrawHandler.Callback {
                    override fun prepared() {
                        // 从 0 起点开始；首次对齐由下面的 LaunchedEffect 接力
                        // （在此处直接访问 player.currentPosition 跨线程不安全）
                        start(0)
                    }
                    override fun updateTimer(timer: DanmakuTimer) {}
                    override fun drawingFinished() {}
                })
                enableDanmakuDrawingCache(false) // 盒子内存紧张，关 cache
                showFPS(false)
                show()
                danmakuView = this
                if (xmlBytes != null && xmlBytes.isNotEmpty()) {
                    DanmakuLoader.buildParser(xmlBytes)?.let { parser ->
                        prepare(parser, context)
                    }
                }
            }
        },
        update = { view ->
            if (visible) view.show() else view.hide()
        },
    )

    // 首次对齐 + pause/resume 跟随（不再每秒 seekTo）
    LaunchedEffect(danmakuView, hasData) {
        val view = danmakuView ?: return@LaunchedEffect
        if (!hasData) return@LaunchedEffect

        // 等 prepared 真正完成（DrawHandler.Callback.prepared 异步），单次对齐到 player 当前位置
        repeat(20) {
            if (view.isPrepared) {
                runCatching { view.seekTo(player.currentPosition) }
                return@repeat
            }
            delay(100)
        }

        // 后续只 pause/resume 跟 player —— 不调 seekTo，避免清空弹幕队列
        while (true) {
            try {
                if (player.isPlaying) {
                    if (view.isPrepared && view.isPaused) view.resume()
                } else if (view.isPrepared && !view.isPaused) {
                    view.pause()
                }
            } catch (_: Throwable) { /* ignore */ }
            delay(500)
        }
    }

    // 监听 player 真的 seek 事件（用户拖动 / 切清晰度 / 跳跃续播）—— 才单次 seekTo 重对齐
    DisposableEffect(player, danmakuView, hasData) {
        val view = danmakuView
        if (view == null || !hasData) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // 只对真正的 seek 类响应；AUTO_TRANSITION 等自然过渡不需要
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    runCatching {
                        if (view.isPrepared) view.seekTo(newPosition.positionMs)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            danmakuView?.release()
            danmakuView = null
        }
    }
}
