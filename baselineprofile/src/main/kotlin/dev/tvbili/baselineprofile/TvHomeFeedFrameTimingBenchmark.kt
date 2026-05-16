package dev.tvbili.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode.Disable
import androidx.benchmark.macro.BaselineProfileMode.Require
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode.WARM
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首页 LazyVerticalGrid 滚动 frame timing 基准。
 *
 * 期望（PRD §Success Metrics）：
 * - `frameDurationCpuMs` P50 ≤ 18 ms（约 55 fps）
 * - 加 baseline profile 后 P50 ≤ 16 ms（60 fps）
 *
 * WARM 启动：每次测前不重启 Activity，模拟用户长时间使用稳态。
 */
@RunWith(AndroidJUnit4::class)
class TvHomeFeedFrameTimingBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeed_compilationNone() = scrollFeed(CompilationMode.None())

    @Test
    fun scrollFeed_partialWithoutBaselineProfile() = scrollFeed(
        CompilationMode.Partial(
            baselineProfileMode = Disable,
            warmupIterations = 2,
        )
    )

    @Test
    fun scrollFeed_partialWithBaselineProfile() = scrollFeed(
        CompilationMode.Partial(
            baselineProfileMode = Require,
        )
    )

    private fun scrollFeed(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = FRAME_TIMING_BENCHMARK_ITERATIONS,
        startupMode = WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        },
    ) {
        scrollGridWithDpad()
    }

    /** D-pad ↓×8 → ↑×8：触发 LazyVerticalGrid 滚动 + tvFocusable scale 动画 */
    private fun MacrobenchmarkScope.scrollGridWithDpad() {
        device.pressDPadRight()
        device.waitForIdle()
        repeat(8) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        repeat(8) {
            device.pressDPadUp()
            device.waitForIdle()
        }
    }
}
