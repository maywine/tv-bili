package dev.tvbili.ui.video

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
fun PlayerSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = false
                setShutterBackgroundColor(AndroidColor.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { view ->
            // 每次重组都重绑一次：因为首次 setPlayer 时 PlayerView 还未 attach 到 window，
            // SurfaceView 的 Surface 未创建；后续重组里再 set 一次确保 ExoPlayer 把视频
            // 输出端口重新接到已经 ready 的 Surface 上——这是避免「黑屏只剩声」的兜底。
            if (view.player !== player) view.player = player
        },
    )
}
