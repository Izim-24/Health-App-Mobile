# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Jetpack Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Room
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Hilt
-keep class com.google.dagger.hilt.** { *; }
-keepclassmembers class * {
    @com.google.dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Domain models
-keep class com.healthapp.domain.model.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class retrofit2.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Gson
-keepattributes Signature
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.JsonDeserializer
-keep class * implements com.google.gson.JsonSerializer
# --- Gson-serialized classes ---------------------------------------------------
# Gson reads/writes JSON keys from Kotlin field names via reflection. Without these
# rules R8 renames fields (image -> a) and even removes "unused" ones (mimeType, role),
# which breaks the AI backend requests and the JSON data export in release builds.
-keep class com.healthapp.data.remote.** { *; }
-keep class com.healthapp.data.local.entity.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Backup restore reads this class with Gson (field names must survive R8).
-keep class com.healthapp.data.repository.BackupFile { *; }

# Built-in Vietnamese food library parses assets/vn-foods.json with Gson.
-keep class com.healthapp.data.food.VietnameseFoodLibrary$* { *; }
