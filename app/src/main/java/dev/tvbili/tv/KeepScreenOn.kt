package dev.tvbili.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * 阻止系统进入屏保 / Daydream / 自动息屏。
 *
 * **必须**在视频 / 直播播放屏挂载期间调用：Android 屏保 / Daydream 只看用户输入空闲
 * 时长，它**不知道** app 在播视频（音频走 audio focus 通道，与屏幕节能策略独立）。
 * 表现就是看到一半 TV 自己跳屏保但还有声。
 *
 * 实现：`View.keepScreenOn = true` 等价于在父 Window 上设 `FLAG_KEEP_SCREEN_ON`，
 * 但不用拿 Activity 引用。挂载期 = true，[onDispose] 自动清除 —— 不会泄漏到其它屏。
 *
 * 注意：本 helper 只拦 Android 层屏保。部分 TV 厂商 OS（TCL/创维/小米/华为等）可能
 * 另有「无操作 N 分钟自动关机」类节能策略，那个在 TV 设置里关，app 层拦不住。
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
