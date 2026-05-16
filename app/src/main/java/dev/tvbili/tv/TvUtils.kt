package dev.tvbili.tv

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Android TV / 盒子设备检测工具。
 *
 * - 单 APK 同代码库，运行时分流 UI。
 * - 一次性检测，结果通过 [LocalIsTvDevice] CompositionLocal 注入下游 Compose 树，
 *   避免每个组件都查询 PackageManager / Configuration。
 */
object TvUtils {

    /**
     * 判定当前设备是否为 TV / 盒子。
     *
     * 命中任一条件即视为 TV：
     * - `Configuration.UI_MODE_TYPE_TELEVISION`：系统级 TV 模式
     * - `FEATURE_LEANBACK`：Android TV 标准
     * - `android.software.leanback_only`：纯 TV（无触摸）
     */
    fun isTv(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_TYPE_MASK
        if (mode == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.software.leanback_only")
    }

    /**
     * 是否为 32-bit 低端设备（无 64-bit ABI）。
     * 盒子里 armeabi-v7a only 设备多为低端 SoC（A53×4 / 1 GB RAM），
     * 视觉特效降级时使用。
     */
    fun isLowEndAbi(): Boolean = Build.SUPPORTED_64_BIT_ABIS.isNullOrEmpty()

    /**
     * 设备总内存（MB）。播放器缓冲 + Coil 内存缓存依此分档：
     *
     * - ≥ 2048 MB（3 GB 中端 TV）：放宽缓冲到 64 MB / 40 s
     * - < 2048 MB（老 1 GB 盒子）：保守 32 MB / 30 s
     *
     * 使用 `ActivityManager.MemoryInfo.totalMem` —— 这是 *系统级* 物理 RAM，
     * 不受应用 heap 限制影响。值在设备生命周期内不变，可缓存。
     */
    @Volatile
    private var cachedTotalMemMb: Long = -1L

    fun totalMemMb(context: Context): Long {
        cachedTotalMemMb.takeIf { it > 0 }?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L)).also { cachedTotalMemMb = it }
    }
}

/**
 * 当前设备是否为 TV。在 [dev.tvbili.MainActivity] 的 setContent 顶层注入；
 * 下游所有 Compose 组件通过 `LocalIsTvDevice.current` 取值。
 *
 * 默认 `false`：未注入时不打开 TV 分支。
 */
val LocalIsTvDevice = staticCompositionLocalOf { false }
