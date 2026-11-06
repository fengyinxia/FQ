-keep class com.fuck.fanqie.MainHook { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage
-keep class org.luckypray.dexkit.** { *; }
-keep class kotlin.Metadata { *; }
# 与宿主反射签名交互：保留原名，防止R8把宿主类型校验字符串改写为模块混淆名。
-keep interface kotlin.jvm.functions.Function0 { *; }
-keep interface kotlin.jvm.functions.Function1 { *; }
-dontwarn kotlin.jvm.internal.SourceDebugExtension
