package com.fuck.fanqie.finders;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.TargetScanResult;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

public final class DownloadFinder extends BaseFinder {
    public DownloadFinder(TargetScanResult scanResult) {
        super(scanResult);
    }

    @Override
    public void find(DexKitBridge bridge) {
        findReaderDirectoryPreloadClass(bridge);
        findStatusDispatcherMethod(bridge);
    }

    private void findReaderDirectoryPreloadClass(DexKitBridge bridge) {
        try {
            ClassData classData = first(bridge.findClass(
                    FindClass.create().matcher(
                            ClassMatcher.create().usingStrings(new String[]{"阅读器目录预加载复用成功"})
                    )
            ));
            cacheClass(HookTargets.KEY_READER_DIRECTORY_PRELOAD_CLASS, classData);
        } catch (Throwable throwable) {
            log("查找阅读器目录预加载类失败", throwable);
        }
    }

    private void findStatusDispatcherMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create().usingStrings(new String[]{"fail to execute percent change: "})
                    )
            ));
            cacheMethod(HookTargets.KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找下载状态分发方法失败", throwable);
        }
    }
}
