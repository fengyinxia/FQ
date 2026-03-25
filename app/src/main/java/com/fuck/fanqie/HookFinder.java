package com.fuck.fanqie;

import com.fuck.fanqie.finders.AdFinder;
import com.fuck.fanqie.finders.FeatureFinder;
import com.fuck.fanqie.finders.UIFinder;

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.XposedBridge;

public class HookFinder {
    private final AdFinder adFinder;
    private final FeatureFinder featureFinder;
    private final UIFinder uiFinder;

    public HookFinder(ClassLoader hostClassLoader, MethodCacheManager cacheManager) {
        adFinder = new AdFinder(hostClassLoader, cacheManager);
        featureFinder = new FeatureFinder(hostClassLoader, cacheManager);
        uiFinder = new UIFinder(hostClassLoader, cacheManager);
    }

    public void findTargets(DexKitBridge bridge) {
        XposedBridge.log("FQHook+findTargets: 开始查找目标");
        adFinder.find(bridge);
        featureFinder.find(bridge);
        uiFinder.find(bridge);
    }
}
