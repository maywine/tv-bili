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
import dev.tvbili.player.WatchdogVerdict
import dev.tvbili.player.decideWatchdog
import dev.tvbili.player.qnAfterDecoderError
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
     * 用户选定清晰度。默认 4K（qn=120）——目标设备能解 4K。
     *
     * - 走 `fnval=4048` + `fourk=1` 才能拿到 4K dash 流；账号不够档时 B 站只返低清，
     *   [StreamSelector] 自动降到 ≤ targetQn 的最高一档（120 → 116 → 112 → 80 → ...）
     * - 兜底：万一某设备/某片源 4K HEVC 硬解失败，[onPlayerError] 的解码分支会自动降到有
     *   H.264 的档（[qnAfterDecoderError]），不会卡死翻 Error——只在真·解码报错时触发，
     *   不影响能跑 4K 的设备
     * - 用户可在 Overlay chip 现场切 720P/1080P
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
    /**
     * 在飞行中的自动恢复协程——单飞守卫：看门狗与 [onPlayerError] 不会并发各起一个恢复，
     * 也覆盖「异步重拉 playurl + re-prepare」的整个窗口，避免看门狗中途再触发把尝试次数双扣。
     */
    private var recoverJob: Job? = null
    /**
     * **连续**自动恢复失败次数；超过 [MAX_AUTO_RECOVER] 才翻 Error。
     * 看门狗一旦观测到位置正常推进（恢复确实生效）即清零（见 [startWatchdog] 的 PROGRESS 分支），
     * 避免一部长电影里几次互不相关的瞬时卡顿累计撞死。
     */
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
         * 处理分流：
         * - **IO/网络类**：自动重拉 playurl + 续播到当前位置（[refreshPlayUrlAfterError]，连续失败封顶）
         * - **解码器类**：同 codec 重拉没用，降清晰度到有 H.264 的档再播（[downgradeQualityAfterDecoderError]）
         * - 其它（渲染等）：翻 Error 让用户重试
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
                // 解码器扛不住（4K HEVC/AV1 硬解失败）：重拉同档没用——setEnableDecoderFallback
                // 只换同 MIME 的解码器，弱盒子没有软解 HEVC/AV1 可退。降清晰度到有 H.264 的档再播。
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED -> downgradeQualityAfterDecoderError(ready)
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

    /**
     * 自动重拉 playurl 续播——同 cid + 同 qn，新 URL 替换过期/出错 URL。
     *
     * - **单飞**：已有恢复在飞行中直接返回，避免看门狗与 [onPlayerError] 并发双扣尝试次数
     * - **续播位置**：一律续到 savedPos（含试看——重拉的是同一段试看流，clip 相对位置有效）；
     *   旧逻辑试看续到 0 会每次从头重缓冲、又被看门狗误判，是「卡顿→重头→再卡」的根因。
     *   超出新时长的 seek 由 ExoPlayer 自动夹到末尾，不会越界。
     * - 连续失败封顶 [MAX_AUTO_RECOVER]；看门狗观测到位置推进会把计数清零（非连续失败不累计）。
     */
    private fun refreshPlayUrlAfterError(ready: VideoDetailState.Ready) {
        if (recoverJob?.isActive == true) return // 单飞守卫
        if (autoRecoverAttempts >= MAX_AUTO_RECOVER) {
            _state.value = VideoDetailState.Error("播放反复失败，请点击重试")
            return
        }
        autoRecoverAttempts++
        val savedPos = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
        recoverJob = viewModelScope.launch {
            runCatching {
                loadPlayUrlOrTryLook(currentBvid, ready.detail.cid, _selectedQn.value)
            }.fold(
                onSuccess = { (newPlayUrl, isTrial) ->
                    setPlayerMedia(newPlayUrl, resumeMs = savedPos)
                    _state.value = ready.copy(playUrl = newPlayUrl, isTrialPlay = isTrial)
                    Log.d(TAG, "auto-recovered @ ${savedPos}ms (attempt $autoRecoverAttempts, trial=$isTrial)")
                },
                onFailure = { e ->
                    Log.e(TAG, "auto-recover failed", e)
                    _state.value = VideoDetailState.Error(e.message ?: "播放出错")
                },
            )
        }
    }

    /**
     * 解码器报错后降清晰度重播：把 qn 一步降到有 H.264 的档（[qnAfterDecoderError]）再走
     * [refreshPlayUrlAfterError]——这样 [StreamSelector] 会选出 codecid=7 的 H.264 流，弱盒子能硬解。
     * 复用 autoRecoverAttempts 上限避免死循环；已无更低档可降时翻 Error 并提示用户手动降画质。
     */
    private fun downgradeQualityAfterDecoderError(ready: VideoDetailState.Ready) {
        if (recoverJob?.isActive == true) return
        val lowered = qnAfterDecoderError(_selectedQn.value)
        if (lowered == null) {
            _state.value = VideoDetailState.Error("当前设备无法解码该视频，请尝试更低画质")
            return
        }
        Log.w(TAG, "decoder error → downgrade qn ${_selectedQn.value} → $lowered")
        _selectedQn.value = lowered
        refreshPlayUrlAfterError(ready)
    }

    /**
     * 看门狗：每 [WATCHDOG_POLL_MS] 轮询一次，把状态采样交给纯函数 [decideWatchdog] 裁决，
     * 只在「真·无声卡死」时走 [refreshPlayUrlAfterError]。
     *
     * 抓的是 ExoPlayer 仍报 [Player.STATE_READY]、本应推进、却连续 [dev.tvbili.player.STUCK_POLLS_BEFORE_RECOVER]
     * 个轮询位置不动的卡死——[Player.Listener.onPlayerError] 抓不到的那一类。
     *
     * **关键修正**：正常重缓冲（[Player.STATE_BUFFERING]）位置冻结是健康的，绝不当卡死；
     * 恢复飞行中（[recoverJob] 活跃）整段跳过；位置正常推进则把 [autoRecoverAttempts] 清零。
     * 这三点合起来根治了「4K 电影重缓冲被误判 → 整流重拉 → 越拉越卡 → 撞满次数翻 Error」。
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            var lastPos = -1L
            var stallStreak = 0
            while (true) {
                delay(WATCHDOG_POLL_MS)
                val s = _state.value
                if (s !is VideoDetailState.Ready) {
                    lastPos = -1L; stallStreak = 0; continue
                }
                val playing = runCatching { player.playWhenReady }.getOrDefault(false)
                val state = runCatching { player.playbackState }.getOrDefault(Player.STATE_IDLE)
                val pos = runCatching { player.currentPosition }.getOrDefault(0L)
                val dur = runCatching { player.duration }.getOrDefault(0L)
                when (
                    decideWatchdog(
                        playbackState = state,
                        playWhenReady = playing,
                        pos = pos,
                        dur = dur,
                        lastPos = lastPos,
                        stallStreak = stallStreak,
                        recovering = recoverJob?.isActive == true,
                    )
                ) {
                    WatchdogVerdict.RECOVER -> {
                        Log.w(TAG, "watchdog: READY but frozen @${pos}ms for ≥$stallStreak polls → auto-recover")
                        lastPos = -1L; stallStreak = 0
                        refreshPlayUrlAfterError(s)
                    }
                    WatchdogVerdict.STUCK_TICK -> stallStreak += 1 // 保留 lastPos，继续累计
                    WatchdogVerdict.PROGRESS -> {
                        lastPos = pos; stallStreak = 0
                        autoRecoverAttempts = 0 // 确认恢复健康 → 连续失败计数清零
                    }
                    WatchdogVerdict.RESET_BASELINE -> {
                        lastPos = pos; stallStreak = 0
                    }
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
        recoverJob?.cancel() // 取消上一个视频可能仍在飞行的恢复，避免它回写到新视频
        recoverJob = null
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
        recoverJob?.cancel()
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
