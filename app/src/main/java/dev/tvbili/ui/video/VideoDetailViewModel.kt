package dev.tvbili.ui.video

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.tvbili.data.model.PlayUrlData
import dev.tvbili.data.model.RelatedVideoItem
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        /**
         * 走了 PGC `try_look=1` fallback——当前流是试看片段（非大会员看的前 5-15 分钟）。
         * UI 据此画「试看中」角标；过期 ENDED 触发自动续播相关视频时也基于此跳过历史进度。
         */
        val isTrialPlay: Boolean = false,
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
        ).also { it.addListener(playerListener) }
    }

    private var currentBvid: String = ""
    /** 多 P 视频里当前播放的分 P 索引（0-based）。 */
    private var currentPageIndex: Int = 0
    private var watchdogJob: Job? = null
    /** 自动恢复尝试次数（一次 load 周期内）；超过则停手避免死循环 */
    private var autoRecoverAttempts: Int = 0

    /** 当前视频的相关推荐列表；播完末 P 时取首个自动续播。在视频 Ready 后异步填充。 */
    private var relatedQueue: List<RelatedVideoItem> = emptyList()
    /** 当 ENDED 触发时若 [relatedQueue] 还没拉回来，先把意图记在这里——拉回后立刻续播。 */
    private var pendingAutoAdvanceOnRelatedReady: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val ready = _state.value as? VideoDetailState.Ready ?: return
            val pages = ready.detail.pages
            // 多 P 未到末 P → 先放下一 P
            if (pages.size > 1 && currentPageIndex + 1 < pages.size) {
                advanceToPage(currentPageIndex + 1)
                return
            }
            // 单 P / 已到末 P → 拉相关视频续播；相关列表还没就位时先记账，loadRelated 完会兜底
            val first = relatedQueue.firstOrNull()
            if (first != null) {
                advanceToRelated(first)
            } else {
                pendingAutoAdvanceOnRelatedReady = true
            }
        }

        /**
         * 长时间播放黑屏的常见根因：
         * 1. DASH segment URL 过期（B 站 CDN URL 含 expires 时间戳，2 h 左右失效）
         * 2. 网络抖动后 ExoPlayer source 反复重试失败最终抛 PlaybackException
         *
         * 处理：仅一次自动重拉 playurl + 续播到当前位置。仍失败则把 UI 翻 Error，
         * 用户可重试。播放器内部错误（解码 / 渲染）不重拉——重拉拿不到不同 codec 帮不了。
         */
        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "player error ${error.errorCode}: ${error.message}")
            val ready = _state.value as? VideoDetailState.Ready ?: run {
                _state.value = VideoDetailState.Error(error.message ?: "播放出错")
                return
            }
            when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> refreshPlayUrlAfterError(ready)
                else -> _state.value = VideoDetailState.Error(error.message ?: "播放出错")
            }
        }
    }

    /**
     * 拉取 playurl，自动 UGC → PGC（try_look=1）fallback。
     *
     * 流程：
     * 1. 先调 UGC `x/player/wbi/playurl`——支持普通投稿视频与多数公开 PGC bvid
     * 2. 失败时无条件再调 PGC `pgc/player/web/playurl?try_look=1`——大会员锁的综艺/番剧/电影
     *    在此分支拿到试看流（前 5-15 分钟）
     * 3. PGC 也失败 → 抛 UGC 的原始异常（信息更贴近用户预期，如「-10403 大会员专享」）
     *
     * @return Pair<playurl 数据, 是否走的试看分支>
     */
    private suspend fun loadPlayUrlOrTryLook(
        bvid: String,
        cid: Long,
        qn: Int,
    ): Pair<PlayUrlData, Boolean> {
        val ugc = repo.loadPlayUrl(bvid, cid, qn)
        if (ugc.isSuccess) return ugc.getOrThrow() to false
        val ugcError = ugc.exceptionOrNull() ?: IllegalStateException("playurl failed")
        Log.d(TAG, "UGC playurl failed (${ugcError.message}); fallback PGC try_look")
        val pgc = repo.loadPgcPlayUrl(bvid, cid, qn)
        return pgc.fold(
            onSuccess = { it to true },
            onFailure = { throw ugcError }, // 仍报原 UGC 错——更贴近用户认知（「大会员专享」之类）
        )
    }

    /** 自动重拉 playurl 续播——同 cid + 同 qn，新 URL 替换过期/出错 URL。次数封顶 [MAX_AUTO_RECOVER]。 */
    private fun refreshPlayUrlAfterError(ready: VideoDetailState.Ready) {
        if (autoRecoverAttempts >= MAX_AUTO_RECOVER) {
            _state.value = VideoDetailState.Error("播放反复失败，请点击重试")
            return
        }
        autoRecoverAttempts++
        val savedPos = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
        viewModelScope.launch {
            runCatching {
                loadPlayUrlOrTryLook(currentBvid, ready.detail.cid, _selectedQn.value)
            }.fold(
                onSuccess = { (newPlayUrl, isTrial) ->
                    setPlayerMedia(newPlayUrl, resumeMs = if (isTrial) 0L else savedPos)
                    _state.value = ready.copy(playUrl = newPlayUrl, isTrialPlay = isTrial)
                    Log.d(TAG, "auto-recovered from playback error @ ${savedPos}ms (attempt $autoRecoverAttempts, trial=$isTrial)")
                },
                onFailure = { e ->
                    Log.e(TAG, "auto-recover failed", e)
                    _state.value = VideoDetailState.Error(e.message ?: "播放出错")
                },
            )
        }
    }

    /**
     * 看门狗：每 [WATCHDOG_POLL_MS] 轮询一次，若 playWhenReady=true 且 currentPosition
     * 连续两次相等（=「上次和这次都没动」），视为「卡死黑屏」走 [refreshPlayUrlAfterError]。
     * 触发窗口 = 1 个 poll 周期；想更激进改 [WATCHDOG_POLL_MS] 即可。
     *
     * 抓的是 ExoPlayer 内部仍报 READY/BUFFERING 但不抛 PlaybackException 的「无声卡死」——
     * [Player.Listener.onPlayerError] 抓不到的那一类。
     *
     * 早退条件（不算卡死）：
     * - state ≠ Ready：起播未完成 / 出错 / 加载中，本来就该不动
     * - playWhenReady=false：用户主动暂停
     * - playbackState=ENDED：正常播完（由 ENDED listener 续播）
     * - 接近末尾（pos ≥ duration - 1s）：最后一秒数值抖动，避免误判
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            var lastPos = -1L
            while (true) {
                delay(WATCHDOG_POLL_MS)
                val s = _state.value
                if (s !is VideoDetailState.Ready) {
                    lastPos = -1L; continue
                }
                val playing = runCatching { player.playWhenReady }.getOrDefault(false)
                val state = runCatching { player.playbackState }.getOrDefault(Player.STATE_IDLE)
                val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                val dur = runCatching { player.duration }.getOrDefault(0L)
                if (!playing || state == Player.STATE_ENDED) {
                    lastPos = pos; continue
                }
                if (dur > 0L && pos >= dur - 1_000L) {
                    lastPos = pos; continue
                }
                // 连续两次同 pos = 至少 1 个 poll 周期没推进 → 自动恢复
                // lastPos == -1L 表示第一次采样，没基线可比，先记下来下轮再说
                if (lastPos != -1L && pos == lastPos) {
                    Log.w(TAG, "watchdog: stuck @${pos}ms for ≥${WATCHDOG_POLL_MS}ms → auto-recover")
                    refreshPlayUrlAfterError(s)
                    lastPos = -1L // 重启采样基线，避免恢复期间又被同 pos 二次触发
                } else {
                    lastPos = pos
                }
            }
        }
    }

    /**
     * 拉相关视频列表填到 [relatedQueue]。
     * 若 ENDED 在结果回来前先到（[pendingAutoAdvanceOnRelatedReady] = true），
     * 这里成功时立刻续播首项；失败则放弃自动续播（不弹错误）。
     */
    private suspend fun prefetchRelated(bvid: String) {
        repo.loadRelated(bvid).fold(
            onSuccess = { list ->
                // bvid 可能在中途切走（用户主动开了别的视频）；只有还在播原 bvid 时才认这结果
                if (currentBvid != bvid) return
                relatedQueue = list
                if (pendingAutoAdvanceOnRelatedReady) {
                    pendingAutoAdvanceOnRelatedReady = false
                    val first = list.firstOrNull() ?: return
                    advanceToRelated(first)
                }
            },
            onFailure = { e ->
                Log.w(TAG, "prefetchRelated($bvid) failed: ${e.message}")
                pendingAutoAdvanceOnRelatedReady = false
            },
        )
    }

    /**
     * 切到相关视频续播——本质等同 [load] 一个新 bvid，复用 player 实例。
     * 走 [load] 主流程：会重置 currentPageIndex / 重启 watchdog / 重拉 detail+playurl+弹幕+related。
     */
    private fun advanceToRelated(target: RelatedVideoItem) {
        Log.d(TAG, "auto-next: $currentBvid → ${target.bvid}")
        load(target.bvid)
    }

    private fun advanceToPage(index: Int) {
        val ready = _state.value as? VideoDetailState.Ready ?: return
        val pages = ready.detail.pages
        val target = pages.getOrNull(index) ?: return
        currentPageIndex = index
        viewModelScope.launch {
            runCatching {
                val (playUrl, isTrial) = loadPlayUrlOrTryLook(currentBvid, target.cid, _selectedQn.value)
                val newDetail = ready.detail.copy(cid = target.cid, duration = target.duration)
                setPlayerMedia(playUrl, resumeMs = 0L)
                _state.value = VideoDetailState.Ready(newDetail, playUrl, null, isTrial)
                // 弹幕跟随分 P 重拉
                launch {
                    val xml = withContext(Dispatchers.IO) {
                        repo.loadDanmakuBytes(target.cid).getOrNull()
                    }
                    val cur = _state.value
                    if (cur is VideoDetailState.Ready && cur.detail.cid == target.cid) {
                        _state.value = cur.copy(danmakuXml = xml)
                    }
                }
                // 顶到历史首位并清掉旧 cid 进度（新 P 算新一轮观看）
                runCatching {
                    historyRepo.recordView(
                        card = HomeCard.Video(
                            aid = newDetail.aid,
                            bvid = currentBvid,
                            title = newDetail.title,
                            coverUrl = newDetail.pic,
                            uploader = newDetail.owner.name,
                            durationSec = newDetail.duration,
                            viewCount = 0,
                        ),
                        cid = target.cid,
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "advanceToPage($index) failed", e)
            }
        }
    }

    fun load(bvid: String) {
        if (bvid.isBlank()) {
            _state.value = VideoDetailState.Error("bvid 为空")
            return
        }
        if (currentBvid == bvid && _state.value is VideoDetailState.Ready) return
        currentBvid = bvid
        currentPageIndex = 0
        autoRecoverAttempts = 0
        relatedQueue = emptyList()
        pendingAutoAdvanceOnRelatedReady = false
        _state.value = VideoDetailState.Loading(bvid)
        startWatchdog()
        viewModelScope.launch {
            // 阶段 1：detail + playurl → 立刻起播。不再等弹幕 XML 下完才 setPlayerMedia，
            // 否则 PlayerSurface 进合成时机被弹幕下载阻塞，ExoPlayer 已经开始解码音频但
            // Surface 还没附上 → 首帧 onRenderedFirstFrame 错过，PlayerView shutter 不掉，
            // 表现为「黑屏 + 声音」直到下次重组（用户按 OK 触发）才恢复。
            runCatching {
                val detail = repo.loadDetail(bvid).getOrThrow()
                val (playUrl, isTrial) = loadPlayUrlOrTryLook(bvid, detail.cid, _selectedQn.value)
                Triple(detail, playUrl, isTrial)
            }.fold(
                onSuccess = { (detail, playUrl, isTrial) ->
                    // 历史进度续播：(bvid, cid) 双键匹配；过早 / 已看完 / 异常数据返 0
                    // 试看片段不续播——试看流时长 ≠ 正片，旧 lastPositionMs 没意义
                    val resumeMs = if (isTrial) 0L else historyRepo.getResumePositionMs(
                        bvid = bvid,
                        cid = detail.cid,
                        durationSec = detail.duration,
                    )
                    if (resumeMs > 0L) Log.d(TAG, "resume @ ${resumeMs}ms for $bvid")
                    setPlayerMedia(playUrl, resumeMs = resumeMs)
                    _state.value = VideoDetailState.Ready(detail, playUrl, null, isTrial)
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
                    // 阶段 3：相关视频列表预拉，供「末 P 看完后自动续播下一个视频」。
                    // 失败 / 空都不影响主播放——只是 ENDED 时不自动续播，等用户按返回。
                    launch { prefetchRelated(bvid) }
                },
                onFailure = { e ->
                    Log.e(TAG, "load($bvid) failed", e)
                    _state.value = VideoDetailState.Error(e.message ?: "加载失败")
                },
            )
        }
    }

    fun retry() {
        if (currentBvid.isBlank()) return
        val keep = currentBvid
        currentBvid = "" // 绕过 load 里 currentBvid==bvid 的早退
        load(keep)
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
            runCatching {
                loadPlayUrlOrTryLook(currentBvid, cur.detail.cid, qn)
            }.fold(
                onSuccess = { (newPlayUrl, isTrial) ->
                    val savedPosition = player.currentPosition
                    setPlayerMedia(newPlayUrl)
                    player.seekTo(savedPosition)
                    _state.value = cur.copy(playUrl = newPlayUrl, isTrialPlay = isTrial)
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
        watchdogJob?.cancel()
        runCatching { player.release() }
    }

    private companion object {
        const val TAG = "VideoDetailVM"
        /** 每个视频 (bvid) 内播放出错后自动重拉 playurl 的次数上限，不跨视频累计。 */
        const val MAX_AUTO_RECOVER = 3
        /**
         * 看门狗轮询间隔；也就是「卡死」检出的最小窗口（连续两次同 pos 即触发，
         * 实际触发时机 = 1～2 个 poll 周期，即 2.5～5 s）。调短更激进、调长更宽容。
         */
        const val WATCHDOG_POLL_MS = 2_500L
    }
}
