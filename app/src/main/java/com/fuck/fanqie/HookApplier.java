package com.fuck.fanqie;

import com.fuck.fanqie.hooks.AdHooks;
import com.fuck.fanqie.hooks.FeatureHooks;
import com.fuck.fanqie.hooks.FrameworkHooks;
import com.fuck.fanqie.hooks.UIHooks;
import com.fuck.fanqie.hooks.download.DownloadHooks;

public class HookApplier {
    private final FrameworkHooks frameworkHooks;
    private final FeatureHooks featureHooks;
    private final AdHooks adHooks;
    private final UIHooks uiHooks;
    private final DownloadHooks downloadHooks;

    public HookApplier(MethodCacheManager cacheManager, ClassLoader hostClassLoader) {
        frameworkHooks = new FrameworkHooks(cacheManager, hostClassLoader);
        featureHooks = new FeatureHooks(cacheManager, hostClassLoader);
        adHooks = new AdHooks(cacheManager, hostClassLoader);
        uiHooks = new UIHooks(cacheManager, hostClassLoader);
        downloadHooks = new DownloadHooks(cacheManager, hostClassLoader);
    }

    public void applyHooks() {
        frameworkHooks.apply();
        featureHooks.apply();
        adHooks.apply();
        uiHooks.apply();
        downloadHooks.apply();
    }
}
