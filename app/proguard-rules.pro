# SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
#
# SPDX-License-Identifier: CC0-1.0

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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontobfuscate

# Required so that Timber's stack trace offset doesn't break
-keep class timber.log.Timber { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.equeim.tremotesf.**$$serializer { *; } # <-- change package name to your app's
-keepclassmembers class org.equeim.tremotesf.** { # <-- change package name to your app's
    *** Companion;
}
-keepclasseswithmembers class org.equeim.tremotesf.** { # <-- change package name to your app's
    kotlinx.serialization.KSerializer serializer(...);
}
