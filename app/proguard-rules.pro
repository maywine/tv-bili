# Phase 7 R8 规则集 —— release 打开 minify + shrinkResources 后用。
#
# 规则范围覆盖：Kotlinx Serialization / Retrofit / OkHttp / Media3 ExoPlayer /
# DanmakuFlameMaster / Coil 3。tv-bili 自身的 DTO 类全保留（@Serializable 反射
# 读取字段名，重命名一旦发生立刻 missingFieldException）。
#
# 调试技巧：release 闪退 / 启动慢时，临时去掉 `-allowaccessmodification` 与
# `-repackageclasses ''`，二分定位是哪条规则的副作用。

# ── 通用 ─────────────────────────────────────────────────────
-allowaccessmodification
-repackageclasses ''
-dontwarn java.lang.invoke.StringConcatFactory   # AGP/Kotlin 2.x 编译噪声

# ── Compose ─────────────────────────────────────────────────
# Compose 编译器已自带运行时 keep；这里只保 @Stable / @Immutable 注解本身
-keep,allowobfuscation @interface androidx.compose.runtime.Stable
-keep,allowobfuscation @interface androidx.compose.runtime.Immutable

# ── Kotlinx Serialization ───────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
# @Serializable 生成的 $$serializer 内部类（每个 model 一个）必须留全
-keep,includedescriptorclasses class dev.tvbili.**$$serializer { *; }
-keepclassmembers class dev.tvbili.** {
    *** Companion;
}
-keepclasseswithmembers class dev.tvbili.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit + OkHttp ───────────────────────────────────────
-keepattributes Signature, Exceptions, *Annotation*
-keepclasseswithmembers,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── DanmakuFlameMaster (ctiao 0.3.8) ────────────────────────
# 内部用 Class.forName 加载 parser；任何 obfuscate 都会 NoClassDefFoundError
-keep class master.flame.danmaku.** { *; }
-keep class tv.cjump.jni.** { *; }

# ── Media3 ExoPlayer ────────────────────────────────────────
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.ui.PlayerView { *; }
-dontwarn com.google.errorprone.annotations.**

# ── Coil 3 ──────────────────────────────────────────────────
-dontwarn coil3.network.cronet.**
-dontwarn org.chromium.**

# ── tv-bili 自身 ────────────────────────────────────────────
# DTO：@Serializable 反射读字段，全留
-keep class dev.tvbili.data.model.** { *; }
# 入口
-keep class dev.tvbili.MainActivity
-keep class dev.tvbili.TvBiliApplication
