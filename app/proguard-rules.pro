# ProGuard / R8 Optimization Rules for AppRetention
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Preserve Xposed Framework interfaces and entry points
-keep class io.github.libxposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
-keep class com.hchen.appretention.HookInit { *; }

# Preserve all AppRetention hooks, reflection data, and UI classes
-keep class com.hchen.appretention.hook.** { *; }
-keep class com.hchen.appretention.data.** { *; }
-keep class com.hchen.appretention.log.** { *; }
-keep class com.hchen.appretention.ui.** { *; }

# Preserve HookTool & Collect libraries
-keep class com.hchen.hooktool.** { *; }
-keep class com.hchen.collect.** { *; }

# Preserve native bridge & low-level libraries
-keep class org.luckypray.dexkit.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }

# Preserve Parcelable CREATORs
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Ignore third-party library compilation warnings
-dontwarn **
