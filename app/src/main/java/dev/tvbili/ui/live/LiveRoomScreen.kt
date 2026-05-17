package dev.tvbili.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import dev.tvbili.tv.KeepScreenOn
import dev.tvbili.tv.tvFocusable
import dev.tvbili.ui.video.PlayerSurface

@UnstableApi
@Composable
fun LiveRoomScreen(
    roomId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LiveRoomViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val danmakuEnabled by vm.danmakuEnabled.collectAsStateWithLifecycle()
    val playbackPhase by vm.playbackPhase.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(false) }
    val rootFocusRequester = remember { FocusRequester() }
    val overlayFocusRequester = remember { FocusRequester() }

    // 直播屏挂载期间阻 Daydream / 屏保
    KeepScreenOn()

    LaunchedEffect(roomId) { vm.load(roomId) }
    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(80)
            runCatching { overlayFocusRequester.requestFocus() }
        } else {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    BackHandler(enabled = controlsVisible) { controlsVisible = false }

    // 按 Home / 切后台必须立刻停声——直播屏 Composable 不会从树上移除，光靠 onDispose
    // 拦不住后台播放，必须监听 Lifecycle.ON_PAUSE。onDispose 兜底页面真正退出时的清理。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                runCatching {
                    vm.player.playWhenReady = false
                    vm.player.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                vm.player.playWhenReady = false
                vm.player.pause()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (controlsVisible) {
                    // 浮层可见：方向键交给 LiveOverlay 内 chip 处理。Up 关菜单的逻辑放
                    // LiveOverlay.onPreviewKeyEvent 里——AnimatedVisibility 子合成边界会
                    // 阻断本层根 Box 的 preview 触达 chip 焦点路径。
                    when (e.key) {
                        // Back 就地收掉浮层；不让外层 BackHandler / MainActivity 抢
                        Key.Back -> { controlsVisible = false; true }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            vm.togglePlayPause(); true
                        }
                        else -> false
                    }
                } else {
                    when (e.key) {
                        // OK：纯播停切换，不开菜单（与点播屏交互一致）
                        // 用 vm.togglePlayPause()——直播暂停后 resume 要跳到 live edge，
                        // 否则 player 续播老位置可能卡在 BUFFERING 永远拉不回
                        Key.DirectionCenter, Key.Enter -> {
                            vm.togglePlayPause(); true
                        }
                        // 仅 Down 开菜单；Up 在菜单隐藏时无操作（语义：Up 永远是"收"）
                        Key.DirectionDown -> { controlsVisible = true; true }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            vm.togglePlayPause(); true
                        }
                        else -> false
                    }
                }
            },
    ) {
        // PlayerSurface 永远挂载——和点播屏同理：SurfaceView 必须在 prepare/playWhenReady=true
        // 之前完成 attach + Surface 创建，否则首批解码帧渲到 null Surface 被丢，下一关键帧
        // （HLS GOP 2-4 s 不等）才能恢复，表现为「黑屏 + 声音」直到外力（按 OK）触发重组重接 Surface。
        // Idle/Loading/Error/Offline 状态下 player 没 mediaSource 只显 PlayerView 黑色 shutter。
        PlayerSurface(player = vm.player, modifier = Modifier.fillMaxSize())

        when (val s = state) {
            LiveRoomState.Idle, is LiveRoomState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is LiveRoomState.Error -> ErrorBlock(
                message = s.message,
                onRetry = { vm.retry() },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )

            is LiveRoomState.Offline -> OfflineBlock(
                title = s.info.title,
                uploader = s.info.uid.toString(),
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )

            is LiveRoomState.Ready -> {
                if (danmakuEnabled) {
                    LiveDanmakuOverlay(
                        messages = vm.danmaku,
                        visible = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 播放阶段 overlay：缓冲 / 已暂停 / 直播结束 —— Playing/Idle 不显示
                when (playbackPhase) {
                    LivePlaybackPhase.Buffering -> BufferingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    LivePlaybackPhase.Paused -> PausedIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    LivePlaybackPhase.Ended -> EndedIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    LivePlaybackPhase.Playing, LivePlaybackPhase.Idle -> Unit
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    LiveOverlay(
                        title = s.info.title,
                        areaName = s.info.areaName,
                        currentQn = s.currentQn,
                        acceptQn = s.acceptQn,
                        qnDesc = s.qnDesc,
                        onSelectQn = { vm.setQn(it) },
                        danmakuEnabled = danmakuEnabled,
                        onToggleDanmaku = { vm.toggleDanmaku() },
                        entryFocusRequester = overlayFocusRequester,
                        onDismissUp = { controlsVisible = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveOverlay(
    title: String,
    areaName: String,
    currentQn: Int,
    acceptQn: List<Int>,
    qnDesc: Map<Int, String>,
    onSelectQn: (Int) -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    entryFocusRequester: FocusRequester,
    onDismissUp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
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
        Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(text = areaName.ifBlank { "直播中" }, color = Color(0xFFB0B0B0), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 清晰度 chips —— accept_qn 倒序展示（原画 → 流畅）；入口焦点挂在第一颗
            acceptQn.forEachIndexed { index, qn ->
                ChipButton(
                    label = qnDesc[qn] ?: "qn=$qn",
                    selected = qn == currentQn,
                    onClick = { onSelectQn(qn) },
                    modifier = if (index == 0) Modifier.focusRequester(entryFocusRequester) else Modifier,
                )
            }
            ChipButton(
                label = if (danmakuEnabled) "弹幕✓" else "弹幕",
                selected = danmakuEnabled,
                onClick = onToggleDanmaku,
            )
        }
    }
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A)
    val fg = if (selected) Color.White else Color(0xFFE0E0E0)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .tvFocusable(cornerRadius = 14.dp, scaleOnFocus = 1.06f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 12.sp) }
}

@Composable
private fun BufferingIndicator(modifier: Modifier = Modifier) {
    // 网络拉不动 / CDN 慢——半透明黑底 + spinner + 文字，避免让用户以为是按错了暂停
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
        Text("缓冲中…", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun PausedIndicator(modifier: Modifier = Modifier) {
    // 用户主动暂停——简洁标识，无 spinner（避免和缓冲混淆）
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⏸  已暂停",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EndedIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("直播已结束", color = Color.White, fontSize = 16.sp)
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("加载失败", color = Color.White, fontSize = 18.sp)
        Text(message, color = Color(0xFFB0B0B0), fontSize = 14.sp)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary)
                .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.08f)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("重试", color = Color.White, fontSize = 14.sp) }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A))
                .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.08f)
                .clickable(onClick = onBack)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("返回", color = Color.White, fontSize = 14.sp) }
    }
}

@Composable
private fun OfflineBlock(
    title: String,
    uploader: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🌙 主播未开播", color = Color.White, fontSize = 18.sp)
        Text(title, color = Color(0xFFB0B0B0), fontSize = 14.sp)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A))
                .tvFocusable(cornerRadius = 8.dp, scaleOnFocus = 1.08f)
                .clickable(onClick = onBack)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("返回", color = Color.White, fontSize = 14.sp) }
    }
}
