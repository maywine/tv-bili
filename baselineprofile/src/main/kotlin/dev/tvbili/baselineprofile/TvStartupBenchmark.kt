package dev.tvbili.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode.Disable
import androidx.benchmark.macro.BaselineProfileMode.Require
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode.COLD
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启基准。4 种 CompilationMode 对比：
 *
 * | 方法 | 模式 | 含义 |
 * |---|---|---|
 * | startupWithoutPreCompilation | None | 无 AOT，纯解释执行（最差基线） |
 * | startupPartialWithoutBaselineProfile | Partial + Disable BP | 走 ART JIT warmup，禁用 profile |
 * | startupPartialWithBaselineProfile | Partial + Require BP | 加载 baseline profile（目标场景）|
 * | startupFullCompilation | Full | 全量 AOT（理论下界）|
 *
 * 期望：`startupPartialWithBaselineProfile` < `startupWithoutPreCompilation` 大约 30% 以上。
 */
@RunWith(AndroidJUnit4::class)
class TvStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupWithoutPreCompilation() = startup(CompilationMode.None())

    @Test
    fun startupPartialWithoutBaselineProfile() = startup(
        CompilationMode.Partial(
            baselineProfileMode = Disable,
            warmupIterations = 2,
        )
    )

    @Test
    fun startupPartialWithBaselineProfile() = startup(
        CompilationMode.Partial(
            baselineProfileMode = Require,
        )
    )

    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = STARTUP_BENCHMARK_ITERATIONS,
        startupMode = COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.waitForIdle()
    }
}
