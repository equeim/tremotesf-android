# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate
-keep class org.qtproject.qt5.android.QtNative { *; }
-keep class org.equeim.libtremotesf.** { *; }
-dontwarn com.simplecityapps.recyclerview_fastscroll.views.FastScrollPopup

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class org.equeim.tremotesf.**$$serializer { *; } # <-- change package name to your app's
-keepclassmembers class org.equeim.tremotesf.** { # <-- change package name to your app's
    *** Companion;
}
-keepclasseswithmembers class org.equeim.tremotesf.** { # <-- change package name to your app's
    kotlinx.serialization.KSerializer serializer(...);
}
