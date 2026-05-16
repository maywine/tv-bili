package dev.tvbili.baselineprofile

/**
 * Macrobenchmark / Baseline Profile 共享常量。
 *
 * - [TARGET_PACKAGE_NAME] 必须和 release `applicationId` 一致；debug 是
 *   `dev.tvbili.debug`，baseline profile 仅对 release 测有意义
 * - 迭代次数：< 5 噪声大，> 12 一轮跑半小时。10/8 是 BiliPai 实测的合理平衡
 */
internal const val TARGET_PACKAGE_NAME = "dev.tvbili"
internal const val STARTUP_BENCHMARK_ITERATIONS = 10
internal const val FRAME_TIMING_BENCHMARK_ITERATIONS = 8
internal const val BASELINE_PROFILE_MAX_ITERATIONS = 8
