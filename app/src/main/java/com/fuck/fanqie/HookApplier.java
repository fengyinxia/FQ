package com.fuck.fanqie;

import com.fuck.fanqie.cache.CachedTargets;
import com.fuck.fanqie.hooks.AdHooks;
import com.fuck.fanqie.hooks.BottomTabHooks;
import com.fuck.fanqie.hooks.FeatureHooks;
import com.fuck.fanqie.hooks.UIHooks;
import com.fuck.fanqie.hooks.SettingsEntryHooks;
import com.fuck.fanqie.hooks.download.DownloadHooks;

public class HookApplier {
    private final FeatureHooks featureHooks;
    private final AdHooks adHooks;
    private final BottomTabHooks bottomTabHooks;
    private final UIHooks uiHooks;
    private final com.fuck.fanqie.hooks.SearchResultHooks searchResultHooks;
    private final DownloadHooks downloadHooks;
    private final SettingsEntryHooks settingsEntryHooks;

    public HookApplier(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        featureHooks = new FeatureHooks(cachedTargets, hostClassLoader);
        adHooks = new AdHooks(cachedTargets, hostClassLoader);
        bottomTabHooks = new BottomTabHooks(cachedTargets, hostClassLoader);
        uiHooks = new UIHooks(cachedTargets, hostClassLoader);
        searchResultHooks = new com.fuck.fanqie.hooks.SearchResultHooks(cachedTargets);
        downloadHooks = new DownloadHooks(cachedTargets, hostClassLoader);
        settingsEntryHooks = new SettingsEntryHooks(cachedTargets, hostClassLoader);
    }

    public void applyHooks() {
        // 恢复入口先安装，不依赖任何业务Hook安装成功。各组失败互不阻断。
        install("设置入口", () -> settingsEntryHooks.apply());
        install("功能", () -> featureHooks.apply());
        install("广告", () -> adHooks.apply());
        install("底栏", () -> bottomTabHooks.apply());
        install("界面", () -> uiHooks.apply());
        install("搜索结果", () -> searchResultHooks.apply());
        install("下载", () -> downloadHooks.apply());
    }

    private static void install(String name, Runnable action) {
        try { action.run(); }
        catch (Throwable error) {
            de.robv.android.xposed.XposedBridge.log("FQHook+HookApplier: " + name + "安装失败，继续其他组");
            de.robv.android.xposed.XposedBridge.log(error);
        }
    }
}
