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
    private static HookApplier hookApplier;
    private static ClassLoader hostClassLoader;
    private static boolean isInitialized;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!TARGET_PACKAGE.equals(loadPackageParam.packageName)) {
            return;
        }

        String processName = loadPackageParam.processName;
        if (processName != null && processName.endsWith(":push")) {
            XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext", Context.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof Application)) return;
                            try {
                                PluginSettings.initialize((Context) param.args[0], false);
                                if (PluginSettings.enabled("push")) {
                                    log("按配置终止推送进程");
                                    Process.killProcess(Process.myPid());
                                }
                            } catch (Throwable throwable) {
                                log("推送配置读取失败，保留宿主进程: " + throwable);
                            }
                        }
                    });
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
            PluginSettings.initialize(context);
            log("已加载本次运行配置；设置修改在重启后生效");
            hostClassLoader = context.getClassLoader();
            final String packageCodePath = context.getPackageCodePath();

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
                log("缓存失效，等待前台页面自动扫描；本次不安装业务Hook");
                new com.fuck.fanqie.hooks.InitialScanHooks(progress ->
                        runDexKitSearch(packageCodePath, currentAppVersion, progress)).install();
            } else {
                log("版本无变化，使用缓存方法，不创建DexKit");
                hookApplier.applyHooks();
            }
        } catch (Throwable throwable) {
            isInitialized = false;
            log("初始化失败: " + throwable.getMessage());
            XposedBridge.log(throwable);
        }
    }

    private long getAppVersionCode(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.getLongVersionCode();
        } catch (Throwable throwable) {
            log("获取应用版本失败: " + throwable);
            return -1L;
        }
    }

    private java.util.List<String> runDexKitSearch(String packageCodePath, long currentAppVersion,
                                                  TargetScanResult.Progress progress) throws Throwable {
        log("后台扫描开始，thread=" + Thread.currentThread().getName());
        progress.log("加载扫描引擎…");
        System.loadLibrary("dexkit");
        TargetScanResult scanResult;
        try (DexKitBridge bridge = DexKitBridge.create(packageCodePath)) {
            scanResult = new HookFinder().findTargets(bridge, progress);
            progress.log("释放扫描资源…");
            log("目标查找完成，正在后台释放DexKit资源");
        }
        // 必须先关闭原生扫描器再发布，关闭失败不能留下本轮成功缓存。
        progress.log("保存扫描缓存…");
        if (!targetRepository.publishFreshScan(currentAppVersion, scanResult)) {
            throw new IllegalStateException("扫描结果为空或缓存保存失败");
        }
        log("后台扫描、释放及发布完成，等待重启，targets=" + scanResult.size());
        return scanResult.failures();
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
