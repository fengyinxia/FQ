package com.fuck.fanqie;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.os.Process;

import com.fuck.fanqie.cache.CachedTargets;
import com.fuck.fanqie.cache.TargetScanResult;
import com.fuck.fanqie.cache.TargetRepository;

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "FQHook";
    private static final String TARGET_PACKAGE = "com.dragon.read";

    private static TargetRepository targetRepository;
    private static CachedTargets cachedTargets;
    private static HookFinder hookFinder;
    private static HookApplier hookApplier;
    private static ClassLoader hostClassLoader;
    private static boolean isInitialized;

    static {
        try {
            System.loadLibrary("dexkit");
        } catch (Throwable throwable) {
            log("加载 dexkit 库失败: " + throwable);
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!TARGET_PACKAGE.equals(loadPackageParam.packageName)) {
            return;
        }

        String processName = loadPackageParam.processName;
        if (processName != null && processName.endsWith(":push")) {
            String applicationClass = loadPackageParam.appInfo == null
                    ? "Unknown"
                    : loadPackageParam.appInfo.className;
            log("检测到推送进程: " + processName + ", Application类: " + applicationClass + ", 执行强制终止");
            try {
                Process.killProcess(Process.myPid());
                System.exit(0);
            } catch (Throwable ignored) {
            }
            return;
        }

        if (processName == null || !processName.equals(loadPackageParam.packageName)) {
            log("跳过非主进程 Hook: " + processName);
            return;
        }

        log("开始加载模块，目标包名: " + loadPackageParam.packageName + " (主进程)");
        XposedHelpers.findAndHookMethod(
                ContextWrapper.class,
                "attachBaseContext",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof Application) {
                            initHooks((Context) param.args[0]);
                        }
                    }
                }
        );
    }

    private synchronized void initHooks(Context context) {
        if (isInitialized || context == null) {
            return;
        }
        isInitialized = true;

        try {
            hostClassLoader = context.getClassLoader();
            String packageCodePath = context.getPackageCodePath();

            targetRepository = new TargetRepository(context, hostClassLoader);
            cachedTargets = new CachedTargets(targetRepository);
            hookApplier = new HookApplier(cachedTargets, hostClassLoader);

            long currentAppVersion = getAppVersionCode(context);
            long cachedAppVersion = targetRepository.getCachedVersionCode();
            int currentModuleVersion = targetRepository.getCurrentModuleVersionCode();
            int cachedModuleVersion = targetRepository.getCachedModuleVersionCode();
            String currentModuleFingerprint = targetRepository.getCurrentModuleFingerprint();
            String cachedModuleFingerprint = targetRepository.getCachedModuleFingerprint();

            log("目标应用版本: " + currentAppVersion + " (缓存: " + cachedAppVersion + ")");
            log("当前模块版本: " + currentModuleVersion + " (缓存: " + cachedModuleVersion + ")");
            log("当前模块指纹: " + currentModuleFingerprint + " (缓存: " + cachedModuleFingerprint + ")");

            boolean shouldRefreshCache = currentAppVersion != cachedAppVersion
                    || !currentModuleFingerprint.equals(cachedModuleFingerprint);

            if (shouldRefreshCache) {
                log("检测到版本变化，开始重新查找方法...");
                hookFinder = new HookFinder();
                if (!runDexKitSearch(packageCodePath, currentAppVersion)) {
                    log("刷新缓存失败，继续使用旧缓存");
                }
            } else {
                log("版本无变化，使用缓存方法");
            }

            hookApplier.applyHooks();
        } catch (Throwable throwable) {
            log("初始化失败: " + throwable.getMessage());
            XposedBridge.log(throwable);
        }
    }

    private long getAppVersionCode(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return PackageInfoCompat.getLongVersionCode(packageInfo);
            }
            return packageInfo.versionCode;
        } catch (Throwable throwable) {
            log("获取应用版本失败: " + throwable);
            return -1L;
        }
    }

    private boolean runDexKitSearch(String packageCodePath, long currentAppVersion) {
        try (DexKitBridge bridge = DexKitBridge.create(packageCodePath)) {
            TargetScanResult scanResult = hookFinder.findTargets(bridge);
            boolean published = targetRepository.publishFreshScan(currentAppVersion, scanResult);
            if (published) {
                log("DexKit 方法查找并发布完成");
            } else {
                log("DexKit 方法查找完成，但发布缓存失败");
            }
            return published;
        } catch (Throwable throwable) {
            log("DexKit 查找失败: " + throwable.getMessage());
            return false;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
