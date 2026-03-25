package com.fuck.fanqie.hooks;

import de.robv.android.xposed.XposedHelpers;

public abstract class BaseHook {
    protected final ClassLoader hostClassLoader;

    protected BaseHook(ClassLoader hostClassLoader) {
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
