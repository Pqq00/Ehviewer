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

# ---- 剥离调试日志（println / printStackTrace）----
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
