package dev.tvbili.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * tv-bili TV / 盒子专用 Baseline Profile 生成器。
 *
 * D-pad 录制，覆盖 TV 专属路径：
 * - `LocalIsTvDevice` / `TvUtils.isTv()` 初始化分支
 * - `tvFocusable()` 视觉反馈（缩放 + 边框 + onFocusChanged）
 * - `SideBar.LaunchedEffect` 首焦 + onFocusChanged 驱动 onSelect
 * - `LazyVerticalGrid.focusRestorer()` 焦点恢复
 * - `VideoDetailScreen` onPreviewKeyEvent 路径 + ExoPlayer prepare
 * - BACK 路径（onPreviewKeyEvent 中 Key.Back 在 controlsVisible 时就地消费）
 *
 * 运行命令：
 * ```bash
 * # 模拟器（需 KVM + ATV 镜像；WSL2 一般不行）
 * ./gradlew :baselineprofile:androidTvApi30BaselineProfileAndroidTest
 *
 * # 真盒子（推荐）
 * ./gradlew :baselineprofile:connectedBaselineProfileAndroidTest
 * ```
 *
 * 产物：`baselineprofile/build/outputs/.../baseline-prof.txt`
 * → 手动复制到 `app/src/main/baseline-prof.txt` 后重新 assembleRelease
 *
 * D-pad 节奏铁律：每次 press 之后必须 waitForIdle()，否则 Compose 重组还没跑完就进入
 * 下一次按键，profile 噪声大、AOT 路径覆盖不完整。
 */
@RunWith(AndroidJUnit4::class)
class TvBaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateTvBaselineProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE_NAME,
            includeInStartupProfile = true,
            maxIterations = BASELINE_PROFILE_MAX_ITERATIONS,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // 1) 启动 → SideBar 首项首焦（LaunchedEffect(Unit) 触发 requestFocus）
            //    冷启路径覆盖 TvUtils.isTv() + LocalIsTvDevice 注入
            device.waitForIdle()

            // 2) SideBar D-pad 上下移动，逐项触发 onFocusChanged → onClick → selectSection
            cycleSideBarWithDpad()

            // 3) 进入卡片网格，覆盖 tvFocusable + focusRestorer + LazyVerticalGrid 滚动
            scrollFeedWithDpad()

            // 4) 进入视频详情 → BACK → 焦点恢复到原卡片（HomeViewModel.pendingGridFocus 路径）
            enterVideoAndReturn()
        }
    }

    /** ↓↓↓↓ 把焦点从首项移到下方各分区，再 ↑↑↑↑ 回去；每次驻留让重组完成 */
    private fun MacrobenchmarkScope.cycleSideBarWithDpad() {
        repeat(3) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(3) {
            device.pressDPadUp()
            device.waitForIdle()
        }
    }

    /** → 跳到内容区；↓×8 → →×2 → ↑×8 触发 tvFocusable scale + focusRestorer */
    private fun MacrobenchmarkScope.scrollFeedWithDpad() {
        device.pressDPadRight()
        device.waitForIdle()
        repeat(6) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(2) {
            device.pressDPadRight()
            device.waitForIdle()
        }
        repeat(6) {
            device.pressDPadUp()
            device.waitForIdle()
        }
    }

    /** OK 进入视频页 → ←/→ 模拟 ±10s seek → BACK 返回 */
    private fun MacrobenchmarkScope.enterVideoAndReturn() {
        device.pressDPadCenter()
        device.wait(Until.findObject(By.pkg(TARGET_PACKAGE_NAME)), 8_000)
        device.waitForIdle()

        // 视频页 ←/→ 触发 vm.seekBy 路径
        device.pressDPadLeft()
        device.waitForIdle()
        device.pressDPadRight()
        device.waitForIdle()

        device.pressBack()
        device.waitForIdle()
    }
}
