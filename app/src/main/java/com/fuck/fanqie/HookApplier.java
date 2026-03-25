package com.fuck.fanqie;

import com.fuck.fanqie.cache.CachedTargets;
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

    public HookApplier(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        frameworkHooks = new FrameworkHooks(hostClassLoader);
        featureHooks = new FeatureHooks(cachedTargets, hostClassLoader);
        adHooks = new AdHooks(cachedTargets, hostClassLoader);
        uiHooks = new UIHooks(cachedTargets, hostClassLoader);
        downloadHooks = new DownloadHooks(hostClassLoader);
    }

    public void applyHooks() {
        frameworkHooks.apply();
        featureHooks.apply();
        adHooks.apply();
        uiHooks.apply();
        downloadHooks.apply();
    }
}
