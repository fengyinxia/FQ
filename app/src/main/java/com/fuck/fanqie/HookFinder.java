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
        return findTargets(bridge, null);
    }

    public TargetScanResult findTargets(DexKitBridge bridge, TargetScanResult.Progress progress) {
        XposedBridge.log("FQHook+findTargets: 开始查找目标");
        TargetScanResult scanResult = new TargetScanResult(progress);
        scanResult.report("扫描广告目标…");
        new AdFinder(scanResult).find(bridge);
        scanResult.report("扫描功能目标…");
        new FeatureFinder(scanResult).find(bridge);
        scanResult.report("扫描界面目标…");
        new UIFinder(scanResult).find(bridge);
        scanResult.report("扫描下载目标…");
        new DownloadFinder(scanResult).find(bridge);
        scanResult.finish(HookTargets.allKeys());
        XposedBridge.log("FQHook+findTargets: 目标查找完成, entryCount=" + scanResult.size());
        return scanResult;
    }
}
