package dev.tvbili.player

import android.content.Context
import android.os.Build
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
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import dev.tvbili.data.model.PlayUrlData
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import okhttp3.OkHttpClient

/**
 * tv-bili Media3 ExoPlayer 工厂 + 媒体源工厂。
 *
 * 设计要点：
 * - **OkHttpDataSource 复用项目 [okHttpClient]**：cookies / UA / Referer 拦截器自动生效
 *   （不像 BiliPai 单独拉一个 `playbackOkHttpClient`）
 * - 普通视频沿用网页请求头；电视节目的 tvMediaClient 会在拦截器中改用电视 UA，
 *   并移除 Origin / Referer，否则电视 CDN 返回 403。
 * - **MergingMediaSource(video, audio)**：B 站 DASH 是视频/音频两条独立 stream URL，
 *   ProgressiveMediaSource 可分别拉 segmented MP4，MergingMediaSource 合并播放
 * - **DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF**：盒子端不引 ffmpeg 扩展，硬解优先
 * - **SeekParameters.CLOSEST_SYNC**：盒子 seek 跳到最近关键帧（不精确但快，PRD §性能）
 */
@UnstableApi
object PlayerBuilder {

    fun buildMediaSource(okHttpClient: OkHttpClient, data: PlayUrlData, targetQn: Int): MediaSource? {
        if (data.isDrm) return null
        val selection = StreamSelector.select(data, targetQn)
        if (selection != null && selection.video.validUrl().isNotBlank()) {
            return buildMediaSource(okHttpClient, selection.video.validUrl(), selection.audio?.validUrl())
        }
        val urls = StreamSelector.progressiveUrls(data)
        if (urls.isEmpty()) return null
        val factory = ProgressiveMediaSource.Factory(
            OkHttpDataSource.Factory(okHttpClient).setDefaultRequestProperties(PLAYBACK_HEADERS),
        )
        val sources = urls.map { url ->
            val item = MediaItem.Builder().setUri(url)
            if (data.format.contains("mp4", ignoreCase = true)) item.setMimeType(MimeTypes.VIDEO_MP4)
            factory.createMediaSource(item.build())
        }
        return if (sources.size == 1) sources.single() else ConcatenatingMediaSource(*sources.toTypedArray())
    }

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
            .setMediaCodecSelector { mimeType, secure, tunneling ->
                val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                // BlueStacks 在 x86 上注册的虚拟 Qualcomm AVC 解码器可能无异常地停止出帧。
                // 仅为这类环境优先使用系统自带的 FFmpeg AVC，ARM 盒子保留原有硬解顺序。
                if (mimeType == MimeTypes.VIDEO_H264 &&
                    Build.SUPPORTED_ABIS.any { it.startsWith("x86") } &&
                    codecs.firstOrNull()?.name == "OMX.qcom.video.decoder.avc"
                ) {
                    codecs.sortedBy { if (it.name == "OMX.ffmpeg.h264.decoder") 0 else 1 }
                } else codecs
            }
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
