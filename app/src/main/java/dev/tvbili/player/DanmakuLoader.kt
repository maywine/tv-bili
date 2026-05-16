package dev.tvbili.player

import android.util.Log
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser
import java.io.ByteArrayInputStream

/**
 * 把解压后的 B 站 XML 弹幕字节 → DanmakuFlameMaster 可用的 [BaseDanmakuParser]。
 *
 * 每次 DanmakuSurface 进入合成都要新建一份 parser —— [BiliDanmukuParser.parse] 会消费
 * 内部 IDataSource，第二次进同一个 parser 就空读了，所以「关→开」时必须重建。
 */
object DanmakuLoader {

    fun buildParser(xmlBytes: ByteArray): BaseDanmakuParser? = try {
        if (xmlBytes.isEmpty()) return null
        val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
        loader.load(ByteArrayInputStream(xmlBytes))
        BiliDanmukuParser().apply { load(loader.dataSource) }
    } catch (t: Throwable) {
        Log.e(TAG, "buildParser failed (${xmlBytes.size} bytes)", t)
        null
    }

    private const val TAG = "DanmakuLoader"
}
