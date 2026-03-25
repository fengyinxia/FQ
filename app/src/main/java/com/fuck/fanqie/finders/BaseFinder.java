package com.fuck.fanqie.finders;

import com.fuck.fanqie.cache.TargetScanResult;

import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.util.List;

import de.robv.android.xposed.XposedBridge;

public abstract class BaseFinder {
    protected final TargetScanResult scanResult;

    protected BaseFinder(TargetScanResult scanResult) {
        this.scanResult = scanResult;
    }

    public abstract void find(org.luckypray.dexkit.DexKitBridge bridge);

    protected void cacheClass(String key, ClassData classData) {
        if (classData == null) {
            return;
        }
        try {
            scanResult.putClass(key, classData);
            XposedBridge.log("FQHook+" + getClass().getSimpleName() + ": 已记录类 " + key);
        } catch (Throwable throwable) {
            log("记录类失败 " + key, throwable);
        }
    }

    protected void cacheMethod(String key, MethodData methodData) {
        if (methodData == null) {
            return;
        }
        try {
            scanResult.putMethod(key, methodData);
            XposedBridge.log("FQHook+" + getClass().getSimpleName() + ": 已记录方法 " + key);
        } catch (Throwable throwable) {
            log("记录方法失败 " + key, throwable);
        }
    }

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
