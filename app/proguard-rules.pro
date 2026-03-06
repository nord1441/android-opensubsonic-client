-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.opensubsonic.client.data.model.** { *; }
-keep class com.opensubsonic.client.api.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
