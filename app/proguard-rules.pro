-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions

# Kotlin metadata (needed for Retrofit suspend functions, Hilt, etc.)
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Retrofit
-keep class retrofit2.** { *; }
-keep,allowobfuscation interface retrofit2.Call
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Keep Retrofit service interfaces (needed for Proxy.newProxyInstance)
-keep,allowobfuscation interface * extends retrofit2.CallAdapter
-keep interface com.opensubsonic.client.api.SubsonicApi { *; }

# App classes
-keep class com.opensubsonic.client.data.model.** { *; }
-keep class com.opensubsonic.client.api.** { *; }
-keep class com.opensubsonic.client.di.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Prevent R8 from stripping generic type info used by Gson reflection
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
