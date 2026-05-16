package dev.tvbili.ui.video

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.tvbili.data.model.PlayUrlData
import dev.tvbili.data.model.VideoDetail
import dev.tvbili.data.repo.HistoryRepository
import dev.tvbili.data.repo.HomeCard
import dev.tvbili.data.repo.VideoRepository
import dev.tvbili.net.NetworkModule
import dev.tvbili.player.PlayerBuilder
import dev.tvbili.player.StreamSelector
import dev.tvbili.player.resolvePlayerBufferPolicy
import dev.tvbili.tv.TvUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 视频详情 + 播放器状态机。 */
sealed interface VideoDetailState {
    data object Idle : VideoDetailState
    data class Loading(val bvid: String) : VideoDetailState
    data class Ready(
        val detail: VideoDetail,
        val playUrl: PlayUrlData,
        /**
         * 解压后的明文 XML 字节；null = 加载/解析失败但仍可看视频。
         * 不直接缓存 parser —— DanmakuFlameMaster 的 IDataSource 是一次性的，
         * 每次 DanmakuSurface 进合成时按 bytes 现场重建 parser。
         */
        val danmakuXml: ByteArray?,
    ) : VideoDetailState
    data class Error(val message: String) : VideoDetailState
}

@UnstableApi
class VideoDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = VideoRepository()
    private val historyRepo = HistoryRepository(application)
    private val isTv: Boolean = TvUtils.isTv(application)

    private val _state = MutableStateFlow<VideoDetailState>(VideoDetailState.Idle)
    val state: StateFlow<VideoDetailState> = _state.asStateFlow()

    /**
     * 用户选定清晰度。默认 4K（qn=120）。
     *
     * - 走 `fnval=4048` + `fourk=1`（[VideoRepository.loadPlayUrl]）才能拿到 4K dash 流
     * - 4K 通常需要大会员，账号不够档时 B 站只返低清；[StreamSelector] 会自动降档到
     *   ≤ targetQn 的最高一档（120 → 116 → 112 → 80 → ...）
     * - x86 模拟器解 4K 会吃力——真机/盒子是目标，模拟器属测试场景，用户可在
     *   Overlay chip 现场切 720P/1080P
     */
    private val _selectedQn = MutableStateFlow(120)
    val selectedQn: StateFlow<Int> = _selectedQn.asStateFlow()

    /** 弹幕默认关闭（用户偏好）；按 OK 唤出浮层后可手动开启。 */
    private val _danmakuEnabled = MutableStateFlow(false)
    val danmakuEnabled: StateFlow<Boolean> = _danmakuEnabled.asStateFlow()

    /** ExoPlayer 单例（生命周期跟 ViewModel）。 */
    val player: ExoPlayer by lazy {
        PlayerBuilder.build(
            context = application,
            okHttpClient = NetworkModule.okHttpClient,
            bufferPolicy = resolvePlayerBufferPolicy(
                isTv = isTv,
                totalMemMb = TvUtils.totalMemMb(application),
            ),
        )
    }

    private var currentBvid: String = ""

    fun load(bvid: String) {
        if (bvid.isBlank()) {
            _state.value = VideoDetailState.Error("bvid 为空")
            return
        }
        if (currentBvid == bvid && _state.value is VideoDetailState.Ready) return
        currentBvid = bvid
        _state.value = VideoDetailState.Loading(bvid)
        viewModelScope.launch {
            // 阶段 1：detail + playurl → 立刻起播。不再等弹幕 XML 下完才 setPlayerMedia，
            // 否则 PlayerSurface 进合成时机被弹幕下载阻塞，ExoPlayer 已经开始解码音频但
            // Surface 还没附上 → 首帧 onRenderedFirstFrame 错过，PlayerView shutter 不掉，
            // 表现为「黑屏 + 声音」直到下次重组（用户按 OK 触发）才恢复。
            runCatching {
                val detail = repo.loadDetail(bvid).getOrThrow()
                val playUrl = repo.loadPlayUrl(bvid, detail.cid, _selectedQn.value).getOrThrow()
                detail to playUrl
            }.fold(
                onSuccess = { (detail, playUrl) ->
                    // 历史进度续播：(bvid, cid) 双键匹配；过早 / 已看完 / 异常数据返 0
                    val resumeMs = historyRepo.getResumePositionMs(
                        bvid = bvid,
                        cid = detail.cid,
                        durationSec = detail.duration,
                    )
                    if (resumeMs > 0L) Log.d(TAG, "resume @ ${resumeMs}ms for $bvid")
                    setPlayerMedia(playUrl, resumeMs = resumeMs)
                    _state.value = VideoDetailState.Ready(detail, playUrl, null)
                    // 进入详情页 = 刷新元信息记录；旧 lastPositionMs 由 [HistoryRepository.recordView]
                    // 内部保留（避免秒退丢进度）。退出时 recordProgress 写覆盖最新位置。
                    launch {
                        runCatching {
                            historyRepo.recordView(
                                card = HomeCard.Video(
                                    aid = detail.aid,
                                    bvid = bvid,
                                    title = detail.title,
                                    coverUrl = detail.pic,
                                    uploader = detail.owner.name,
                                    durationSec = detail.duration,
                                    viewCount = 0,
                                ),
                                cid = detail.cid,
                            )
                        }.onFailure { Log.w(TAG, "history record failed: ${it.message}") }
                    }
                    // 阶段 2：弹幕异步追加。完成后只在同 cid 上回写，避免污染已切走的视频
                    launch {
                        val xml = withContext(Dispatchers.IO) {
                            repo.loadDanmakuBytes(detail.cid).getOrNull()
                        }
                        val cur = _state.value
                        if (cur is VideoDetailState.Ready && cur.detail.cid == detail.cid) {
                            _state.value = cur.copy(danmakuXml = xml)
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "load($bvid) failed", e)
                    _state.value = VideoDetailState.Error(e.message ?: "加载失败")
                },
            )
        }
    }

    fun retry() {
        if (currentBvid.isNotBlank()) load(currentBvid)
    }

    fun seekBy(deltaMs: Long) {
        val p = player
        val target = (p.currentPosition + deltaMs).coerceIn(0, p.duration.coerceAtLeast(0))
        p.seekTo(target)
    }

    fun togglePlayPause() {
        val p = player
        if (p.isPlaying) p.pause() else p.play()
    }

    fun toggleDanmaku() {
        _danmakuEnabled.value = !_danmakuEnabled.value
    }

    fun selectQn(qn: Int) {
        if (qn == _selectedQn.value) return
        _selectedQn.value = qn
        val cur = _state.value as? VideoDetailState.Ready ?: return
        viewModelScope.launch {
            repo.loadPlayUrl(currentBvid, cur.detail.cid, qn).fold(
                onSuccess = { newPlayUrl ->
                    val savedPosition = player.currentPosition
                    setPlayerMedia(newPlayUrl)
                    player.seekTo(savedPosition)
                    _state.value = cur.copy(playUrl = newPlayUrl)
                },
                onFailure = { e ->
                    Log.e(TAG, "selectQn($qn) failed", e)
                },
            )
        }
    }

    /**
     * 离开视频屏时调（onDispose）：把当前播放进度回写历史。
     * - 早返条件：currentBvid 空 / position ≤ 0（避免覆盖前次有意义进度为 0）
     * - HistoryStore 内部对 bvid 不存在历史时不补建条目；recordProgress 是 no-op 友好
     */
    fun recordProgress() {
        val bvid = currentBvid.ifBlank { return }
        val pos = runCatching { player.currentPosition }.getOrDefault(0L)
        if (pos <= 0L) return
        viewModelScope.launch {
            runCatching { historyRepo.recordProgress(bvid, pos) }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 4.0f))
    }

    /**
     * @param resumeMs >0 表示从该位置开始播；0 表示从头。
     *
     * 用 [ExoPlayer.setMediaSource] 的双参重载一步把媒体源 + 起始位置原子设置好；
     * 不要先 `setMediaSource(source)` 再 `seekTo(resumeMs)` —— 那时 player 还在 STATE_IDLE
     * 且 timeline 为空，seekTo 行为不可靠，会导致 prepare 完成后 playWhenReady=true 不能
     * 自动起播，需要外力（按 OK）才能踢动。
     */
    private fun setPlayerMedia(playUrl: PlayUrlData, resumeMs: Long = 0L) {
        val selection = StreamSelector.select(playUrl, _selectedQn.value)
        if (selection == null) {
            _state.value = VideoDetailState.Error("无可播放流")
            return
        }
        val source = PlayerBuilder.buildMediaSource(
            okHttpClient = NetworkModule.okHttpClient,
            videoUrl = selection.video.validUrl(),
            audioUrl = selection.audio?.validUrl(),
        )
        if (resumeMs > 0L) {
            player.setMediaSource(source, resumeMs)
        } else {
            player.setMediaSource(source)
        }
        player.prepare()
        player.playWhenReady = true
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { player.release() }
    }

    private companion object {
        const val TAG = "VideoDetailVM"
    }
}
