# ProGuard rules for HyTik
-keep class com.aliablip.hytik.data.api.models.** { *; }
-keep class com.aliablip.hytik.data.db.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Jsoup
-dontwarn org.jsoup.**
