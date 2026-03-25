package com.fuck.fanqie.hooks;

import com.fuck.fanqie.MethodCacheManager;

import de.robv.android.xposed.XposedHelpers;

public abstract class BaseHook {
    protected final MethodCacheManager cacheManager;
    protected final ClassLoader hostClassLoader;

    protected BaseHook(MethodCacheManager cacheManager, ClassLoader hostClassLoader) {
        this.cacheManager = cacheManager;
        this.hostClassLoader = hostClassLoader;
    }

    public abstract void apply();

    protected String getFieldSafely(Object target, String fieldName, String defaultValue) {
        try {
            return (String) XposedHelpers.getObjectField(target, fieldName);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}
