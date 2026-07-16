# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.Interpreter { *; }
-dontwarn org.tensorflow.lite.gpu.**
-dontwarn org.tensorflow.lite.support.**

# Firebase/Google Sign-In
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# GSON & Models (CRITICAL FOR SAVING CONTACTS)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.example.android.models.** { *; }
-keepclassmembers class com.example.android.models.** {
    <fields>;
    <init>(...);
}

# Android Crypto
-keep class androidx.security.crypto.** { *; }

# Android Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
