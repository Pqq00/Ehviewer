# ---- 应用自身 ----
-keep class app.eros.shared.** { *; }
-keep class app.eros.android.** { *; }
-dontwarn app.eros.**

# ---- EhViewer 核心（反射 / fastjson 序列化）----
-keep class com.hippo.ehviewer.client.data.** { *; }
-keep class com.hippo.ehviewer.client.parser.** { *; }
-keep class com.hippo.ehviewer.client.exception.** { *; }
-dontwarn com.hippo.ehviewer.**

# ---- fastjson ----
-dontwarn com.alibaba.fastjson.**
-keep class com.alibaba.fastjson.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# ---- kotlinx.serialization（Settings 持久化）----
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn kotlinx.serialization.**

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Coil ----
-dontwarn coil3.**

# ---- 调试日志 ----
# 不要对 java.io.PrintStream 加 -assumenosideeffects：
# 诊断日志靠「把 System.out 换成 TeeStream」来收集现有 println(...) 的输出，
# 一旦 R8 把 println 当无副作用删掉，release 包里诊断日志就只剩 AppLog 自己写的那几行。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
