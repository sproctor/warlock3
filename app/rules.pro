-dontobfuscate
-ignorewarnings
-dontoptimize

-keepclasseswithmembers public class warlockfe.warlock3.app.MainKt {
    public static void main(java.lang.String[]);
}

-dontwarn kotlinx.coroutines.debug.*

-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class okio.* { *; }
-keep class dev.hydraulic.conveyor.control.* { *; }

-keep class org.apache.commons.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.slf4j.simple.SimpleServiceProvider { *; }
-keep class androidx.compose.foundation.text.TextLinkScope { *; }
-keep class com.sun.jna.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class io.github.vinceglb.filekit.dialogs.** { *; }

# Coil
-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Just keep everything from Warlock
-keep class warlockfe.warlock3.** { *; }