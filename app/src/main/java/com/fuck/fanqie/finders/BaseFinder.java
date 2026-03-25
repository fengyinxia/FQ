package com.fuck.fanqie.finders;

import com.fuck.fanqie.MethodCacheManager;

import java.util.List;

import de.robv.android.xposed.XposedBridge;

public abstract class BaseFinder {
    protected final ClassLoader hostClassLoader;
    protected final MethodCacheManager cacheManager;

    protected BaseFinder(ClassLoader hostClassLoader, MethodCacheManager cacheManager) {
        this.hostClassLoader = hostClassLoader;
        this.cacheManager = cacheManager;
    }

    public abstract void find(org.luckypray.dexkit.DexKitBridge bridge);

    protected void log(String message) {
        XposedBridge.log("FQHook+" + getClass().getSimpleName() + ": " + message);
    }

    protected void log(String message, Throwable throwable) {
        XposedBridge.log("FQHook+" + getClass().getSimpleName() + ": " + message + ": " + throwable.getMessage());
        XposedBridge.log(throwable);
    }

    protected static <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }
}
