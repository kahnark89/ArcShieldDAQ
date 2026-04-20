# Keep CIAER+ schema data classes for kotlinx.serialization
-keep @kotlinx.serialization.Serializable class com.arcshield.app.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
