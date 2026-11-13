# App ProGuard rules
-dontobfuscate

-optimizations !class/merging/*

# Keep utility classes used by Application and services
-keep class com.kingzcheung.xime.util.** { *; }

-dontwarn com.sun.nio.file.**
-dontwarn kotlin.Cloneable$DefaultImpls

# 插件系统（宿主侧 com.kingzcheung.xime.plugin.* + plugin-core）整体保留：
# JS 桥/序列化/动态约定多，防止 R8 裁剪造成 release 行为缺失
# （库侧自完备规则见 plugin-core/consumer-rules.pro）
-keep class kotlin.Metadata { *; }

-keep class com.kingzcheung.xime.plugin.** { *; }
-keepclassmembers class com.kingzcheung.xime.plugin.** { *; }

-keep class com.kingzcheung.xime.rime.** { *; }
-keep class com.kingzcheung.xime.**Jni** { *; }

# ExecuTorch：JNI 侧按类名字符串反射查找 Module/Tensor/EValue 与各 Tensor 子类，
# 被 R8 裁剪或改名后会在 forward 时抛 NoSuchMethodError（表现为 libfbjni 的
# logassert("ptr") → SIGABRT）。整体保留，不做混淆。
-keep class org.pytorch.executorch.** { *; }
-keepclassmembers class org.pytorch.executorch.** { *; }
-keep class com.facebook.jni.** { *; }
-keep class com.facebook.fbjni.** { *; }
-dontwarn org.pytorch.executorch.**
-dontwarn com.facebook.jni.**
-dontwarn com.facebook.fbjni.**

-keepattributes SourceFile,LineNumberTable

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

-processkotlinnullchecks remove