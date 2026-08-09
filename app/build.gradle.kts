plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.tvbili"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.tvbili"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.1.8"

        vectorDrawables { useSupportLibrary = true }

        ndk {
            // 32-bit ARM only — 自用盒子兼容性最佳
            abiFilters += listOf("armeabi-v7a")
        }
    }

    // 锁定单 APK，禁止 ABI split 出多个产物
    splits {
        abi {
            isEnable = false
        }
    }

    // 显式启用 v1 + v2 + v3 签名 —— MuMu / 雷电 等基于旧 Android ROM 改的模拟器
    // 部分镜像只认 v1 (JAR signing) 即「META-INF/*.SF」，纯 v2 APK 会静默拒绝安装。
    // 用独立的 sideload signingConfig 复用 debug keystore 但强制开 v1。
    signingConfigs {
        create("sideload") {
            val debugKs = signingConfigs.getByName("debug")
            storeFile = debugKs.storeFile
            storePassword = debugKs.storePassword
            keyAlias = debugKs.keyAlias
            keyPassword = debugKs.keyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            // Phase 7: R8 minify + 资源裁剪开启；proguard-rules.pro 含 Kotlinx
            // Serialization / Retrofit / Media3 / DanmakuFlameMaster 等 keep 规则
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 自用：release 使用 sideload 签名（debug keystore + v1/v2/v3 全开）
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 模拟器测试专用：debug 包打全 ABI，覆盖各类 AVD ATV 镜像
            //   - armeabi-v7a：和真盒子 release 对齐
            //   - x86：API 24-29 的老 ATV 镜像（32-bit Intel）
            //   - x86_64：API 30+ 的新 ATV 镜像（64-bit Intel）
            // release 仍然单 armeabi-v7a（PRD 硬约束），互不影响。
            ndk {
                abiFilters.clear()
                abiFilters += listOf("armeabi-v7a", "x86", "x86_64")
            }
            // 仅 debug 排除 libndkbitmap.so（DanmakuFlameMaster 0.3.8 只发布
            // armeabi-v7a 版本）；多 ABI 下 x86_64 缺该 .so 会触发 Android 12+
            // 的 INSTALL_FAILED_NO_MATCHING_ABIS。release 单 ABI 不受影响，
            // 真盒子继续走 native 弹幕加速。
            packaging {
                jniLibs.excludes += listOf("**/libndkbitmap.so")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Phase 1: network + storage + QR
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)

    // Phase 2: image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Phase 4: video playback + danmaku
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.danmaku.flame.master)

    // Phase 5: 直播间 HLS / FLV 拉流 + 弹幕 WebSocket（Brotli 解压）
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.brotli.dec)

    // Phase 7: baseline profile 运行时安装器（自动激活 assets/dexopt/baseline.prof）
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation("junit:junit:4.13.2")
}
