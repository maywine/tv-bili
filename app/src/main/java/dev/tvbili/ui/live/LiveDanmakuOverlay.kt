package dev.tvbili.ui.live

import android.graphics.Color as AndroidColor
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
import dev.tvbili.player.DanmakuConfig
import kotlinx.coroutines.flow.Flow
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.IDataSource
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.ui.widget.DanmakuView

/**
 * 直播弹幕浮层：push-based。订阅 [messages] Flow，每条消息调用
 * [DanmakuView.addDanmaku] 即时推一条滚动弹幕。
 *
 * 与 [dev.tvbili.ui.video.DanmakuSurface]（视频版，由 parser 一次性灌完的 XML 弹幕）
 * 的关键区别：直播没有结束时间，DanmakuView 必须 `start(0)` 一直跑，靠 [BaseDanmaku.time]
 * = 当前时间戳来插入。
 */
@Composable
fun LiveDanmakuOverlay(
    messages: Flow<LiveDanmakuMessage>,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var danmakuView by remember { mutableStateOf<DanmakuView?>(null) }
    val context = remember { DanmakuConfig.create() }
    // 空 parser：DanmakuView.prepare 要一个 parser；live 没有数据源，给个空 Danmakus 即可
    val emptyParser: BaseDanmakuParser = remember { EmptyDanmakuParser() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            DanmakuView(ctx).apply {
                setCallback(object : DrawHandler.Callback {
                    override fun prepared() { start(0) }
                    override fun updateTimer(timer: DanmakuTimer) {}
                    override fun drawingFinished() {}
                })
                enableDanmakuDrawingCache(false)
                showFPS(false)
                show()
                danmakuView = this
                prepare(emptyParser, context)
            }
        },
        update = { view ->
            if (visible) view.show() else view.hide()
        },
    )

    // 订阅消息流，逐条添加弹幕到 view
    LaunchedEffect(danmakuView, messages) {
        val view = danmakuView ?: return@LaunchedEffect
        messages.collect { msg ->
            val item = makeDanmaku(context, view, msg) ?: return@collect
            view.addDanmaku(item)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            danmakuView?.release()
            danmakuView = null
        }
    }
}

private fun makeDanmaku(
    context: DanmakuContext,
    view: DanmakuView,
    msg: LiveDanmakuMessage,
): BaseDanmaku? {
    val factory = context.mDanmakuFactory ?: return null
    val danmaku = factory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL, context) ?: return null
    danmaku.text = msg.text
    danmaku.padding = 5
    danmaku.priority = 0
    danmaku.isLive = true
    // 起始时间 = 当前播放时间，确保立即出现在屏幕右侧
    danmaku.time = view.currentTime + 100
    danmaku.textSize = 32f * (context.displayer.density - 0.6f)
    // 颜色：服务端给 24-bit RGB；DanmakuFlameMaster 要 ARGB
    val rgb = msg.color and 0xFFFFFF
    danmaku.textColor = AndroidColor.rgb((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    danmaku.textShadowColor = AndroidColor.BLACK
    danmaku.borderColor = AndroidColor.TRANSPARENT
    return danmaku
}

/** DanmakuView 必须挂一个 parser；直播没有静态源，喂空集合即可。 */
private class EmptyDanmakuParser : BaseDanmakuParser() {
    override fun parse(): IDanmakus = Danmakus()
}
