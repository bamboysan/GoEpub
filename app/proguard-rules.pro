# ==============================================================================
# Aturan ProGuard / R8 untuk Rilis Play Store GoEpub
# ==============================================================================

# Jsoup HTML/XML Parser
-keeppackagenames org.jsoup.nodes
-keep public class org.jsoup.** {
    public *;
}
-dontwarn org.jsoup.**

# Google ML Kit On-Device Translation & Language Identification
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**

# WorkManager Worker
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
