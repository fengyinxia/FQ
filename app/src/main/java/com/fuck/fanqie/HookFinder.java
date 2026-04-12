package com.fuck.fanqie;

import com.fuck.fanqie.cache.TargetScanResult;
import com.fuck.fanqie.finders.AdFinder;
import com.fuck.fanqie.finders.DownloadFinder;
import com.fuck.fanqie.finders.FeatureFinder;
import com.fuck.fanqie.finders.UIFinder;

import org.luckypray.dexkit.DexKitBridge;

import de.robv.android.xposed.XposedBridge;

public class HookFinder {
    public TargetScanResult findTargets(DexKitBridge bridge) {
        XposedBridge.log("FQHook+findTargets: 开始查找目标");
        TargetScanResult scanResult = new TargetScanResult();
        new AdFinder(scanResult).find(bridge);
        new FeatureFinder(scanResult).find(bridge);
        new UIFinder(scanResult).find(bridge);
        new DownloadFinder(scanResult).find(bridge);
        XposedBridge.log("FQHook+findTargets: 目标查找完成, entryCount=" + scanResult.size());
        return scanResult;
    }
}
