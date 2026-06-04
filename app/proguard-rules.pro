# Project specific ProGuard rules

# Standard Android rules
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# R8 fix for missing classes
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.slf4j.impl.**

# Data Models & Serialization
-keep class com.musicplayer.app.data.model.** { *; }
-keep class com.musicplayer.app.data.remote.model.** { *; }
-keep class com.musicplayer.app.data.local.database.entity.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# Kotlin Serialization specific
-keepclassmembers class com.musicplayer.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.musicplayer.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class com.musicplayer.app.data.local.database.** { *; }
-dontwarn androidx.room.**
-keep class com.musicplayer.app.data.local.database.**_Impl { *; }

# Hilt / Dagger
-keep class dagger.hilt.android.internal.** { *; }
-keep class com.musicplayer.app.Hilt_* { *; }
-keep class com.musicplayer.app.**_HiltModules { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.EntryPoint class *
-keep @com.google.dagger.hilt.android.internal.lifecycle.HiltViewModel class *
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep interface dagger.hilt.internal.GeneratedComponent { *; }
-keep interface dagger.hilt.internal.ComponentEntryPoint { *; }
-keep interface dagger.hilt.internal.GeneratedComponentManager { *; }
-keep interface dagger.hilt.internal.UnsafeCasts { *; }
-keep class com.musicplayer.app.MusicApplication_HiltComponents** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# YouTube-DL and FFmpeg
-keep class com.yausername.** { *; }
-keep interface com.yausername.** { *; }
-keep class io.github.junkfood02.** { *; }
-keep interface io.github.junkfood02.** { *; }
-dontwarn com.yausername.**
-dontwarn io.github.junkfood02.**

# Jackson (used by youtubedl-android)
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Apache Commons (used by youtubedl-android)
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# Keep our utility class just in case
-keep class com.musicplayer.app.util.YoutubeDlUtil { *; }
-keepclassmembers class com.musicplayer.app.util.YoutubeDlUtil { *; }

# Ktor & OkHttp
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Retrofit & Gson
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# JSoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Misc
-dontnote com.musicplayer.app.**
-dontwarn com.musicplayer.app.MusicApplication
-keep class com.musicplayer.app.MainActivity { *; }
-keep class com.musicplayer.app.BuildConfig { *; }
-keep class com.musicplayer.app.service.** { *; }
-keep class com.musicplayer.app.widget.** { *; }
-keep class com.musicplayer.app.worker.** { *; }
