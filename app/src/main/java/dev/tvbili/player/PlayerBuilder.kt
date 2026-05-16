package dev.tvbili.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient

/**
 * tv-bili Media3 ExoPlayer 工厂 + 媒体源工厂。
 *
 * 设计要点：
 * - **OkHttpDataSource 复用项目 [okHttpClient]**：cookies / UA / Referer 拦截器自动生效
 *   （不像 BiliPai 单独拉一个 `playbackOkHttpClient`）
 * - **Referer 显式注入**：B 站 CDN 校验 Referer，必须 `https://www.bilibili.com`
 *   —— OkHttpDataSource 的 defaultRequestProperties 优先级**高于** OkHttp 拦截器对该 host 的 header 重写
 * - **MergingMediaSource(video, audio)**：B 站 DASH 是视频/音频两条独立 stream URL，
 *   ProgressiveMediaSource 可分别拉 segmented MP4，MergingMediaSource 合并播放
 * - **DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF**：盒子端不引 ffmpeg 扩展，硬解优先
 * - **SeekParameters.CLOSEST_SYNC**：盒子 seek 跳到最近关键帧（不精确但快，PRD §性能）
 */
@UnstableApi
object PlayerBuilder {

    private val PLAYBACK_HEADERS = mapOf(
        "Referer" to "https://www.bilibili.com",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    )

    fun build(
        context: Context,
        okHttpClient: OkHttpClient,
        bufferPolicy: PlayerBufferPolicy,
    ): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(PLAYBACK_HEADERS)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferPolicy.minBufferMs,
                bufferPolicy.maxBufferMs,
                bufferPolicy.bufferForPlaybackMs,
                bufferPolicy.bufferForPlaybackAfterRebufferMs,
            )
            .setTargetBufferBytes(bufferPolicy.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(bufferPolicy.prioritizeTimeOverSizeThresholds)
            .build()

        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /**
     * 构造视频+音频 MergingMediaSource。
     * 视频流 URL 必填；音频可选（极少数番剧场景只返 video，本期把 audio 视为空集兜底 mute 播放也比崩好）。
     *
     * **必须显式标 MIME**：B 站 DASH 流以 `.m4s?…` 结尾，URL 没标准扩展名。不告诉 ExoPlayer
     * 这是 fragmented MP4 时，`DefaultExtractorsFactory` 会逐个 probe，弱机 / 模拟器上常常
     * 选错 extractor 导致首帧黑屏、A/V drift、音画不同步。显式 `VIDEO_MP4` / `AUDIO_MP4`
     * 让 `FragmentedMp4Extractor` 立刻接管，按 sidx 索引正确切片。
     */
    fun buildMediaSource(
        okHttpClient: OkHttpClient,
        videoUrl: String,
        audioUrl: String?,
    ): MediaSource {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(PLAYBACK_HEADERS)
        val factory = ProgressiveMediaSource.Factory(dataSourceFactory)

        val videoItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .build()
        val video = factory.createMediaSource(videoItem)
        if (audioUrl.isNullOrEmpty()) return video
        val audioItem = MediaItem.Builder()
            .setUri(audioUrl)
            .setMimeType(MimeTypes.AUDIO_MP4)
            .build()
        val audio = factory.createMediaSource(audioItem)
        return MergingMediaSource(video, audio)
    }
}
