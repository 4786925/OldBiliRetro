# OldBiliRetro ProGuard 规则（老工程手写规则，清晰即可）

# ---- IJK Player（JNI 方法必须保留）----
-keep class tv.danmaku.ijk.media.player.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- DanmakuFlameMaster ----
-keep class master.flame.danmaku.** { *; }
-keep class tv.cjump.jni.** { *; }

# ---- SwetakeQRCode ----
-keep class com.swetake.util.** { *; }

# ---- support-v4 / RecyclerView / CardView（21.0.0 源码 vendor）----
-keep class android.support.v4.** { *; }
-keep class android.support.v7.widget.** { *; }
-keep class android.support.v7.cardview.** { *; }
-dontwarn android.support.**
-dontwarn android.support.v4.**

# ---- org.json ----
-keep class org.json.** { *; }

# ---- 序列化/回调反射不涉及，保留注解 ----
-keepattributes *Annotation*
