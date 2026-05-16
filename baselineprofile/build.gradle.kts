plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.tvbili.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // macrobenchmark 要求 ≥ 28；为了上 ATV/盒子真机 baseline profile，保留 28
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 模拟器跑 benchmark 时绕过 EMULATOR 警告；上真机时这条无副作用
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW_BATTERY"
    }

    targetProjectPath = ":app"
    // self-instrumenting：让 macrobenchmark 不走传统 instrumented test，而是用 perfetto+UiAutomator 启动目标 APK
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        managedDevices {
            allDevices {
                // ATV API 30 模拟器；WSL2 无 KVM 跑不动时改走 connectedBaselineProfileAndroidTest 上真盒子
                create<com.android.build.api.dsl.ManagedVirtualDevice>("androidTvApi30") {
                    device = "Television (1080p)"
                    apiLevel = 30
                    systemImageSource = "android-tv"
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
