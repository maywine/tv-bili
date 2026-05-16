package dev.tvbili.ui.live

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.tvbili.data.model.LiveRoomInfo
import dev.tvbili.data.repo.LiveRoomRepository
import dev.tvbili.data.store.TokenStore
import dev.tvbili.net.NetworkModule
import dev.tvbili.net.socket.LiveDanmakuClient
import dev.tvbili.net.socket.LiveDanmakuProtocol
import dev.tvbili.player.PlayerBuilder
import dev.tvbili.player.resolvePlayerBufferPolicy
import dev.tvbili.tv.TvUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 直播间状态机。 */
sealed interface LiveRoomState {
    data object Idle : LiveRoomState
    data class Loading(val roomId: Long) : LiveRoomState
    data class Ready(
        val info: LiveRoomInfo,
        val streamUrl: String,
        val isHls: Boolean,
        val currentQn: Int,
        val acceptQn: List<Int>,
        val qnDesc: Map<Int, String>,
    ) : LiveRoomState
    data class Error(val message: String) : LiveRoomState
    data class Offline(val info: LiveRoomInfo) : LiveRoomState
}

/**
 * 播放器实时阶段——从 ExoPlayer 状态机推导出来，专为 UI 区分「用户暂停」vs「网络卡顿」。
 *
 * 推导规则：
 * - playWhenReady=false → [Paused]：用户主动按 OK / 媒体键暂停了
 * - playWhenReady=true && state=BUFFERING → [Buffering]：网络拉不动 / CDN 慢
 * - playWhenReady=true && state=READY → [Playing]
 * - state=ENDED → [Ended]：直播结束（主播下播）
 * - state=IDLE → [Idle]：未 prepare
 */
enum class LivePlaybackPhase { Idle, Buffering, Playing, Paused, Ended }

@UnstableApi
class LiveRoomViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LiveRoomRepository()
    private val isTv: Boolean = TvUtils.isTv(application)

    private val _state = MutableStateFlow<LiveRoomState>(LiveRoomState.Idle)
    val state: StateFlow<LiveRoomState> = _state.asStateFlow()

    /** 弹幕默认关闭（与视频页保持一致）；浮层里挑「弹幕」chip 即可开。 */
    private val _danmakuEnabled = MutableStateFlow(false)
    val danmakuEnabled: StateFlow<Boolean> = _danmakuEnabled.asStateFlow()

    // 直播弹幕：从 WebSocket 包 → DANMU_MSG 解析 → 推给 UI
    private val _danmaku = MutableSharedFlow<LiveDanmakuMessage>(
        replay = 0,
        extraBufferCapacity = 200,
    )
    val danmaku: SharedFlow<LiveDanmakuMessage> = _danmaku.asSharedFlow()

    val player: ExoPlayer by lazy {
        PlayerBuilder.build(
            context = application,
            okHttpClient = NetworkModule.okHttpClient,
            bufferPolicy = resolvePlayerBufferPolicy(
                isTv = isTv,
                totalMemMb = TvUtils.totalMemMb(application),
            ),
        ).also { it.addListener(playerListener) }
    }

    /** 实时播放阶段——见 [LivePlaybackPhase]。UI 用这个状态显示「缓冲中」/「已暂停」叠加。 */
    private val _playbackPhase = MutableStateFlow(LivePlaybackPhase.Idle)
    val playbackPhase: StateFlow<LivePlaybackPhase> = _playbackPhase.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) { updatePhase() }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { updatePhase() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updatePhase() }
    }

    private fun updatePhase() {
        val p = player
        _playbackPhase.value = when {
            p.playbackState == Player.STATE_IDLE -> LivePlaybackPhase.Idle
            p.playbackState == Player.STATE_ENDED -> LivePlaybackPhase.Ended
            !p.playWhenReady -> LivePlaybackPhase.Paused
            p.playbackState == Player.STATE_BUFFERING -> LivePlaybackPhase.Buffering
            else -> LivePlaybackPhase.Playing
        }
    }

    private val danmakuClient = LiveDanmakuClient(viewModelScope)
    private var enteredRoomId: Long = 0L

    init {
        // 把 ws 收到的 message 包解析成 LiveDanmakuMessage
        viewModelScope.launch {
            danmakuClient.messageFlow.collect { packet ->
                if (packet.operation != LiveDanmakuProtocol.OP_MESSAGE) return@collect
                LiveDanmakuMessageParser.parse(packet.body)?.let { _danmaku.tryEmit(it) }
            }
        }
    }

    fun load(roomId: Long) {
        if (roomId <= 0L) {
            _state.value = LiveRoomState.Error("room_id 非法")
            return
        }
        if (enteredRoomId == roomId && _state.value is LiveRoomState.Ready) return
        enteredRoomId = roomId
        _state.value = LiveRoomState.Loading(roomId)

        viewModelScope.launch {
            runCatching {
                // 1. 短号 → 真 room_id
                val info = repo.getRoomInfo(roomId).getOrThrow()
                val realId = info.roomId.takeIf { it > 0 } ?: roomId

                if (!info.isLiving) {
                    _state.value = LiveRoomState.Offline(info)
                    return@launch
                }

                // 2. 拉流地址（默认请求原画 10000，服务端会按可用最高档回）
                val playInfo = repo.getPlayInfo(realId, qn = 10000).getOrThrow()
                val selection = LiveStreamSelector.select(playInfo)
                    ?: error("无可用拉流地址")

                Log.d(TAG, "selected stream protocol=${selection.protocol} format=${selection.format} qn=${selection.currentQn}")
                setPlayerMedia(selection)
                _state.value = LiveRoomState.Ready(
                    info = info,
                    streamUrl = selection.url,
                    isHls = selection.isHls,
                    currentQn = selection.currentQn,
                    acceptQn = selection.acceptQn,
                    qnDesc = selection.qnDesc,
                )

                // 3. 弹幕 WebSocket
                val danmu = repo.getDanmuInfo(realId).getOrThrow()
                val host = danmu.hostList.firstOrNull() ?: error("无弹幕 host")
                danmakuClient.connect(
                    url = host.wssUrl(),
                    token = danmu.token,
                    roomId = realId,
                    uid = TokenStore.mid,
                )
            }.onFailure { e ->
                Log.e(TAG, "load($roomId) failed", e)
                _state.value = LiveRoomState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun retry() {
        if (enteredRoomId > 0L) load(enteredRoomId)
    }

    fun toggleDanmaku() {
        _danmakuEnabled.value = !_danmakuEnabled.value
    }

    /**
     * 直播专用切换播放/暂停。
     *
     * Resume 时**强制 [ExoPlayer.stop] + [ExoPlayer.prepare]**：
     *
     * 直播 HLS 暂停期间 live edge 会推进，老的 segment 可能已被新 manifest 移除。仅
     * `seekToDefaultPosition() + play()` 不可靠——概率性卡 STATE_BUFFERING 拉不回，
     * 表面 phase=Playing（playWhenReady=true && cached READY 态）但实际无帧出，UI 啥都
     * 不显。stop()→IDLE 释放老 loader，prepare() 重新拉 manifest 自动落到 live edge，
     * playWhenReady=true 起播——完整重启路径保证状态机一致，可被 [playerListener] 追踪。
     *
     * 短暂停（< 2 s）也走同一路径——re-prepare HLS manifest 很轻（一个小 m3u8），
     * 不值得为「短暂停免一次缓冲」加路径分支。
     *
     * 用 `playWhenReady`（用户意图）而非 `isPlaying`（实际出帧）判定——后者在 BUFFERING
     * 时为 false 会让 OK 误判为「想恢复」陷入死循环。
     */
    fun togglePlayPause() {
        val p = player
        if (p.playWhenReady) {
            p.pause()
        } else {
            runCatching {
                p.stop()      // → STATE_IDLE，保留 source
                p.prepare()   // → STATE_BUFFERING，重新拉 manifest 落到 live edge
            }
            p.playWhenReady = true
        }
    }

    /**
     * 切换清晰度：仅重拉 playurl + 换 player media source，房间信息和弹幕 WebSocket 保持不动。
     * 同档位调用 = no-op。
     */
    fun setQn(qn: Int) {
        val current = _state.value as? LiveRoomState.Ready ?: return
        if (current.currentQn == qn) return
        val realId = current.info.roomId.takeIf { it > 0 } ?: enteredRoomId

        viewModelScope.launch {
            runCatching {
                val playInfo = repo.getPlayInfo(realId, qn = qn).getOrThrow()
                val selection = LiveStreamSelector.select(playInfo)
                    ?: error("无可用拉流地址")
                setPlayerMedia(selection)
                _state.value = current.copy(
                    streamUrl = selection.url,
                    isHls = selection.isHls,
                    currentQn = selection.currentQn,
                    acceptQn = selection.acceptQn,
                    qnDesc = selection.qnDesc,
                )
            }.onFailure { e -> Log.e(TAG, "setQn($qn) failed", e) }
        }
    }

    private fun setPlayerMedia(s: LiveStreamSelector.Selection) {
        val dataSource = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://live.bilibili.com",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                ),
            )

        val source: MediaSource = if (s.isHls) {
            HlsMediaSource.Factory(dataSource)
                .createMediaSource(MediaItem.fromUri(s.url))
        } else {
            // FLV / fmp4 走 Progressive；显式标 MIME 避免猜错 extractor
            val item = MediaItem.Builder()
                .setUri(s.url)
                .setMimeType(if (s.format == "fmp4") MimeTypes.VIDEO_MP4 else MimeTypes.VIDEO_FLV)
                .build()
            ProgressiveMediaSource.Factory(dataSource).createMediaSource(item)
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { danmakuClient.disconnect() }
        runCatching { player.release() }
    }

    private companion object {
        const val TAG = "LiveRoomVM"
    }
}
