package com.fuck.fanqie.hooks;

import org.json.JSONObject;

import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FrameworkHooks extends BaseHook {
    public FrameworkHooks(ClassLoader hostClassLoader) {
        super(hostClassLoader);
    }

    @Override
    public void apply() {
        applyForestUtilsHooks();
        // applyThreadUtilsHooks();
        applyReportManagerHooks();
        applyNpthHooks();
    }

    public void applyForestUtilsHooks() {
        try {
            Class<?> threadUtilsClass = XposedHelpers.findClass("com.bytedance.forest.utils.ThreadUtils", hostClassLoader);
            XC_MethodReplacement replacement = XC_MethodReplacement.returnConstant(null);

            XposedHelpers.findAndHookMethod(threadUtilsClass, "runInBackground", Runnable.class, replacement);
            XposedHelpers.findAndHookMethod(threadUtilsClass, "postInSingleThread", Runnable.class, replacement);
            XposedHelpers.findAndHookMethod(threadUtilsClass, "postInSingleThread", Runnable.class, long.class, replacement);
            XposedHelpers.findAndHookMethod(threadUtilsClass, "runInReportThread", Runnable.class, replacement);
            XposedHelpers.findAndHookMethod(threadUtilsClass, "postIdleTask", Runnable.class, replacement);

            XposedBridge.log("FQHook+applyForestUtilsHooks: 已全量拦截 Forest ThreadUtils (资源/上报)");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyForestUtilsHooks: Hook 失败 (可能是类未加载或版本不匹配): ", throwable);
        }
    }

    public void applyReportManagerHooks() {
        try {
            Class<?> reportManagerClass = XposedHelpers.findClass("com.dragon.read.report.ReportManager", hostClassLoader);
            Class<?> argsClass = XposedHelpers.findClass("com.dragon.read.base.Args", hostClassLoader);
            XC_MethodReplacement replacement = XC_MethodReplacement.returnConstant(null);

            XposedHelpers.findAndHookMethod(reportManagerClass, "internalReport", String.class, JSONObject.class, replacement);
            XposedHelpers.findAndHookMethod(reportManagerClass, "onReport", String.class, argsClass, boolean.class, replacement);
            XposedHelpers.findAndHookMethod(reportManagerClass, "onReport", String.class, JSONObject.class, boolean.class, replacement);
            XposedHelpers.findAndHookMethod(reportManagerClass, "onReport", String.class, Map.class, replacement);
            XposedHelpers.findAndHookMethod(reportManagerClass, "postMsgAsync", Runnable.class, replacement);

            XposedBridge.log("FQHook+applyReportManagerHooks: 已全量拦截 ReportManager (埋点上报)");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyReportManagerHooks: Hook ReportManager 失败: ", throwable);
        }
    }

    public void applyNpthHooks() {
        try {
            Class<?> npthClass = XposedHelpers.findClass("com.bytedance.crash.Npth", hostClassLoader);
            XC_MethodReplacement replacement = XC_MethodReplacement.returnConstant(null);

            XposedBridge.hookAllMethods(npthClass, "init", replacement);
            XposedBridge.hookAllMethods(npthClass, "initSDK", replacement);
            XposedBridge.hookAllMethods(npthClass, "initMiniApp", replacement);
            XposedBridge.hookAllMethods(npthClass, "reportError", replacement);
            XposedBridge.hookAllMethods(npthClass, "reportBizException", replacement);
            XposedBridge.hookAllMethods(npthClass, "reportDartError", replacement);
            XposedBridge.hookAllMethods(npthClass, "reportGameException", replacement);
            XposedBridge.hookAllMethods(npthClass, "startOptMtkBuffer", replacement);
            XposedBridge.hookAllMethods(npthClass, "registerCrashCallback", replacement);
            XposedBridge.hookAllMethods(npthClass, "setAttachUserData", replacement);

            XposedBridge.log("FQHook: 已全量拦截 Npth (Crash监控) 初始化及上报");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook: Npth 拦截失败: ", throwable);
        }
    }

    public void applyThreadUtilsHooks() {
        try {
            Class<?> threadUtilsClass = XposedHelpers.findClass("com.dragon.read.base.util.ThreadUtils", hostClassLoader);
            String[] methodNames = {"postInBackground", "postInForeground", "postEmergencyTask", "runInBackground"};
            for (final String methodName : methodNames) {
                XposedHelpers.findAndHookMethod(threadUtilsClass, methodName, Runnable.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Runnable runnable = (Runnable) param.args[0];
                        if (runnable != null) {
                            XposedBridge.log("FQHook+ThreadUtils: [LOG] " + methodName + " <- " + runnable.getClass().getName());
                        }
                    }
                });
            }

            XposedHelpers.findAndHookMethod(threadUtilsClass, "runInMain", Runnable.class, long.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Runnable runnable = (Runnable) param.args[0];
                    if (runnable != null) {
                        XposedBridge.log("FQHook+ThreadUtils: [LOG] runInMain <- " + runnable.getClass().getName());
                    }
                }
            });
            XposedBridge.log("FQHook+applyThreadUtilsHooks: 已启用日志模式 (仅记录，不拦截)");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyThreadUtilsHooks: Hook ThreadUtils 失败: ", throwable);
        }
    }
}
