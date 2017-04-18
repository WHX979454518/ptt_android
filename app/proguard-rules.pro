# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/fanchao/Tools/android-sdk-macosx/tools/proguard/proguard-android.txt
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

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# hockeyapp
-keep class net.hockeyapp.**

-dontwarn com.google.common.**
-dontwarn io.requery.**
-dontwarn com.fasterxml.**

-keepattributes *Annotation*,EnclosingMethod,Signature
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-keep class org.codehaus.** { *; }
-keepclassmembers public final enum org.codehaus.jackson.annotate.JsonAutoDetect$Visibility {
public static final org.codehaus.jackson.annotate.JsonAutoDetect$Visibility *; }
-keep public class your.class.** {
  public void set*(***);
  public *** get*();
}
-keepclassmembers public class * {
     @com.fasterxml.jackson.annotation.JsonCreator *;
}

# Webrtc
-keep class org.webrtc.** { *; }
-keepnames class org.webrtc.** { *; }
-keep class cn.netptt.** { *; }

# 310 datetime
-keep class org.threeten.bp.** { *; }
-keepnames class org.threeten.bp.** { *; }
-keepnames class com.jakewharton.** { *; }
-keep class com.jakewharton.** { *; }

# Retrofit
-dontwarn retrofit2.**

# Gson
-keepattributes Signature
-keep class com.xianzhitech.ptt.data.** { *; }
-keep class com.xianzhitech.ptt.api.dto.** { *; }
-keep class com.xianzhitech.ptt.api.event.** { *; }

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-keep class sun.misc.Unsafe { *; }

# Crashylitics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-printmapping mapping.txt

-keepnames class rx.**
-keepnames class android.**
-keep class android.support.v7.widget.SearchView { *; }

-dontwarn okio.**
-dontwarn rx.**
-dontwarn com.xianzhitech.ptt.**


# Logs
-keep class ch.qos.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation*

-dontwarn ch.qos.logback.core.net.*
-dontwarn org.slf4j.*

# Rx

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

# Baidu
-keep class com.baidu.**
-keepnames class com.baidu.**
-keepnames class com.baidu.** { *; }
-keepclassmembernames class com.baidu.** { *; }
-keepclassmembers class com.baidu.** { *; }
-dontwarn com.baidu.**
-keep class vi.com.** {*;}