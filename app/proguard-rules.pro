-keep class com.topjohnwu.superuser.** { *; }
-keep class com.montfort.garnetforge.** { *; }
-keepattributes *Annotation*
# libsu
-keep class com.topjohnwu.superuser.** { *; }
# Kotlin coroutines
-keepnames class kotlinx.coroutines.** { *; }
# App sealed classes
-keep class dev.garnetforge.app.SpeedTestState* { *; }
-keep class dev.garnetforge.app.DiagnosticState* { *; }
# Compose
-keep class androidx.compose.** { *; }
