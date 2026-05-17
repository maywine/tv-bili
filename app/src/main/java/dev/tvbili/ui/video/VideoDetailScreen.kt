package dev.tvbili.ui.video

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun VideoDetailScreen(
    bvid: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: VideoDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selectedQn by vm.selectedQn.collectAsStateWithLifecycle()
    val danmakuEnabled by vm.danmakuEnabled.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(0L) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    var currentMs by remember { mutableLongStateOf(0L) }
    // 简化进度条：按 OK / seek 时短暂显示；与 controlsVisible 互斥
    var progressBarVisible by remember { mutableStateOf(false) }
    var progressBumpTs by remember { mutableLongStateOf(0L) }

    val rootFocusRequester = remember { FocusRequester() }
    val overlayFocusRequester = remember { FocusRequester() }

    // 视频屏挂载期间阻 Daydream / 屏保（Android 屏保只看用户输入空闲，不看媒体播放）
    KeepScreenOn()

    // 启动加载
    LaunchedEffect(bvid) { vm.load(bvid) }

    // 进入屏幕后立刻把焦点抓到根 Box，确保 onPreviewKeyEvent 能收到事件。
    // Error 态除外——那时焦点要让给 ErrorBlock 的「重试 / 返回」按钮。
    LaunchedEffect(Unit) {
        if (state !is VideoDetailState.Error) {
            try { rootFocusRequester.requestFocus() } catch (_: Throwable) {}
        }
    }

    // 控件浮层显隐时切换焦点目标：
    // - 显 → 焦点跳到第一颗清晰度 chip，D-pad 立刻能横移挑画质/弹幕/速度
    // - 隐 → 焦点回根 Box，方向键回到 seek / 弹出控件的 player 操作语义
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            // AnimatedVisibility 需要一帧才挂上 layout，等动画启动后再请求
            delay(80)
            runCatching { overlayFocusRequester.requestFocus() }
        } else {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Back 优先收起浮层；浮层已隐时让 Back 透传退出页面
    BackHandler(enabled = controlsVisible) { controlsVisible = false }
    // Error 态：root Box 的 focusable 会吞掉一次 Back（焦点态变化消费），直接由这层
    // BackHandler 接管，单次返回即出页面。
    BackHandler(enabled = state is VideoDetailState.Error) { onBack() }

    // 暂停策略：
    // 1) ON_PAUSE（Home / 切应用 / 锁屏）→ 立刻 pause。本项目不做后台播放，避免「按 Home
    //    回桌面后声音还在响」的体感。Home 键只触发 ON_PAUSE/ON_STOP，不会 dispose 当前
    //    Composable，所以单靠 onDispose 拦不住。
    // 2) onDispose（页面真正退出）→ 落盘进度 + pause；不 release()，ViewModel 仍持有同一
    //    ExoPlayer 实例，下次进入可复用 surface。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                runCatching {
                    vm.recordProgress()
                    vm.player.playWhenReady = false
                    vm.player.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                vm.recordProgress()
                vm.player.playWhenReady = false
                vm.player.pause()
            }
        }
    }

    // 4s 自动隐藏控制条
    LaunchedEffect(controlsVisible, lastInteractionMs) {
        if (controlsVisible) {
            delay(4000)
            if (System.currentTimeMillis() - lastInteractionMs >= 4000) {
                controlsVisible = false
            }
        }
    }

    // 2.5s 自动隐藏简化进度条（独立计时器；按 OK/seek 反复触发会顺延）
    LaunchedEffect(progressBumpTs) {
        if (progressBumpTs <= 0L) return@LaunchedEffect
        delay(2500)
        if (System.currentTimeMillis() - progressBumpTs >= 2500) {
            progressBarVisible = false
        }
    }

    fun showProgressBar() {
        progressBarVisible = true
        progressBumpTs = System.currentTimeMillis()
    }

    // 跟踪播放进度（仅用于 Overlay 显示）
    LaunchedEffect(state) {
        if (state !is VideoDetailState.Ready) return@LaunchedEffect
        while (true) {
            currentMs = vm.player.currentPosition
            delay(500)
        }
    }

    fun bump() { lastInteractionMs = System.currentTimeMillis() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Error 态完全让出方向键 / Back：让 ErrorBlock 按钮的焦点系统接管；
                // Back 由上面 enabled=Error 的 BackHandler 处理（单次返回）。
                if (state is VideoDetailState.Error) return@onPreviewKeyEvent false
                // Back 不算交互——别 bump，否则按返回会顺手重置 4s 自动隐藏定时器
                if (e.key != Key.Back) bump()
                if (controlsVisible) {
                    // 浮层可见：方向键交给 overlay 内的 chips 处理。Up 关菜单的语义放在
                    // VideoOverlay.onPreviewKeyEvent 里——AnimatedVisibility 子合成边界会
                    // 阻断本层根 Box 的 preview 触达 chip 焦点路径，故只能贴近焦点本身挂。
                    when (e.key) {
                        // Back 在这里就地收掉浮层——避免和外层 MainActivity 的 BackHandler
                        // 抢 OnBackPressedDispatcher 的 LIFO，需要按两次的根因就是这个抖动
                        Key.Back -> {
                            controlsVisible = false; true
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            vm.togglePlayPause(); true
                        }
                        else -> false
                    }
                } else {
                    // 浮层隐藏：方向键作 player 操作语义。
                    when (e.key) {
                        Key.DirectionLeft -> {
                            vm.seekBy(-10_000); showProgressBar(); true
                        }
                        Key.DirectionRight -> {
                            vm.seekBy(+10_000); showProgressBar(); true
                        }
                        // OK：纯播放/暂停切换 + 短暂显示进度条（不开完整控制菜单）
                        Key.DirectionCenter, Key.Enter -> {
                            vm.togglePlayPause()
                            showProgressBar()
                            true
                        }
                        // 仅向下键唤出浮层——挑画质/弹幕/速度。向上键留空（无菜单时无意义，
                        // 且与「显时 Up 关菜单」语义对称：Up 永远代表"收/隐"，Down 代表"开/显"）
                        Key.DirectionDown -> {
                            controlsVisible = true; true
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            vm.togglePlayPause(); showProgressBar(); true
                        }
                        else -> false
                    }
                }
            },
    ) {
        // PlayerSurface 必须 **永远挂载**——SurfaceView 要在 ExoPlayer.prepare/playWhenReady=true
        // 之前完成 attach + Surface 创建。否则首批解码帧渲到 null Surface 被丢，下一关键帧
        // （DASH 上 GOP 4-5s）才能恢复，表现为「黑屏 + 声音」直到外力（按 OK / 切清晰度）
        // 触发重组重新接 Surface。
        //
        // 状态相关的 UI（loading 指示器、错误块、Overlay）都叠在它上面。Player 在 Loading
        // 状态下没有 mediaSource 只显黑色 shutter（PlayerView 默认背景），看起来就是 loading。
        PlayerSurface(player = vm.player, modifier = Modifier.fillMaxSize())

        when (val s = state) {
            VideoDetailState.Idle, is VideoDetailState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is VideoDetailState.Error -> {
                ErrorBlock(
                    message = s.message,
                    onRetry = { vm.retry() },
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is VideoDetailState.Ready -> {
                if (danmakuEnabled) {
                    DanmakuSurface(
                        player = vm.player,
                        xmlBytes = s.danmakuXml,
                        visible = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 试看中角标——右上角小红章，提示「这是非大会员的试看片段」；
                // 与浮层 / 进度条互不阻挡，始终可见
                if (s.isTrialPlay) {
                    Text(
                        text = "试看",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                // 简化进度条 —— 与完整菜单互斥（菜单显时不渲，避免叠加）
                AnimatedVisibility(
                    visible = progressBarVisible && !controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    MiniProgressBar(
                        currentMs = currentMs,
                        durationMs = vm.player.duration.coerceAtLeast(0L),
                    )
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    VideoOverlay(
                        title = s.detail.title,
                        uploader = s.detail.owner.name,
                        currentMs = currentMs,
                        durationMs = vm.player.duration.coerceAtLeast(0L),
                        acceptQuality = s.playUrl.acceptQuality,
                        acceptDescription = s.playUrl.acceptDescription,
                        selectedQn = selectedQn,
                        onSelectQn = { vm.selectQn(it); bump() },
                        danmakuEnabled = danmakuEnabled,
                        onToggleDanmaku = { vm.toggleDanmaku(); bump() },
                        speed = currentSpeed,
                        onSelectSpeed = { currentSpeed = it; vm.setPlaybackSpeed(it); bump() },
                        entryFocusRequester = overlayFocusRequester,
                        onDismissUp = { controlsVisible = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryRequester = remember { FocusRequester() }
    // 进 Error 态自动把焦点钉在「重试」上——root Box 已让出方向键拦截，按钮的
    // tvFocusable 系统自然处理左右切换
    LaunchedEffect(Unit) {
        runCatching { retryRequester.requestFocus() }
    }
    val friendly = remember(message) { friendlyErrorMessage(message) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("加载失败", color = Color.White, fontSize = 18.sp)
        Text(friendly, color = Color(0xFFB0B0B0), fontSize = 14.sp)
        Box(
            modifier = Modifier
                .focusRequester(retryRequester)
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

/**
 * 把 B 站原始错误码 / 文案映射成对用户更友好的提示。
 * - `-404 / 啥都木有`：非大会员请求会员专属内容（电影/4K）时常见，B 站后端直接拒绝
 * - `-403 / 访问权限`：地区/版权拦截
 * 其它走原文。
 */
private fun friendlyErrorMessage(raw: String): String = when {
    raw.contains("-404") || raw.contains("啥都木有") ->
        "该视频可能需要大会员权限或已下架\n（原始: $raw）"
    raw.contains("-403") || raw.contains("访问权限") || raw.contains("地区") ->
        "该视频在当前账号 / 地区不可播放\n（原始: $raw）"
    else -> raw
}
