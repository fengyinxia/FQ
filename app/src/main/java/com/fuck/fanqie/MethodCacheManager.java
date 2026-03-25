package com.fuck.fanqie;

import android.content.Context;
import android.content.SharedPreferences;

import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.wrap.DexClass;
import org.luckypray.dexkit.wrap.DexMethod;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

public class MethodCacheManager {
    private static final String PREF_NAME = "dex_kit";
    private static final String VERSION_CODE_KEY = "version_code";
    private static final String MODULE_VERSION_CODE_KEY = "module_version_code";
    private static final int CURRENT_MODULE_VERSION_CODE = 20405;

    private final SharedPreferences preferences;
    private final ClassLoader hostClassLoader;
    private SharedPreferences.Editor batchEditor;

    public MethodCacheManager(Context context, ClassLoader hostClassLoader) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.hostClassLoader = hostClassLoader;
    }

    public void beginBatchEdit() {
        batchEditor = preferences.edit();
    }

    public void commitBatch() {
        if (batchEditor != null) {
            batchEditor.apply();
            batchEditor = null;
        }
    }

    public void clearAll() {
        preferences.edit().clear().apply();
        XposedBridge.log("FQHook+MethodCacheManager: 已清空所有缓存");
    }

    public void cacheClass(String key, ClassData classData) {
        if (classData == null) {
            return;
        }
        try {
            putString(key, classData.toDexType().serialize());
            XposedBridge.log("FQHook+MethodCacheManager: 已缓存类 " + key);
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+MethodCacheManager: 缓存类失败 " + key + ": " + throwable.getMessage());
        }
    }

    public void cacheMethod(String key, MethodData methodData) {
        if (methodData == null) {
            return;
        }
        try {
            putString(key, methodData.toDexMethod().serialize());
            XposedBridge.log("FQHook+MethodCacheManager: 已缓存方法 " + key);
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+MethodCacheManager: 缓存方法失败 " + key + ": " + throwable.getMessage());
        }
    }

    public long getCachedVersionCode() {
        return preferences.getLong(VERSION_CODE_KEY, 0L);
    }

    public int getCachedModuleVersionCode() {
        return preferences.getInt(MODULE_VERSION_CODE_KEY, 0);
    }

    public int getCurrentModuleVersionCode() {
        return CURRENT_MODULE_VERSION_CODE;
    }

    public void updateVersionCode(long versionCode) {
        preferences.edit()
                .putLong(VERSION_CODE_KEY, versionCode)
                .putInt(MODULE_VERSION_CODE_KEY, CURRENT_MODULE_VERSION_CODE)
                .apply();
    }

    public Class<?> getClass(String key) {
        String serialized = preferences.getString(key, null);
        if (serialized == null) {
            return null;
        }
        try {
            return new DexClass(serialized).getInstance(hostClassLoader);
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+MethodCacheManager: 获取缓存类失败 " + key + ": " + throwable.getMessage());
            return null;
        }
    }

    public Method getMethod(String key) {
        String serialized = preferences.getString(key, null);
        if (serialized == null) {
            return null;
        }
        try {
            return new DexMethod(serialized).getMethodInstance(hostClassLoader);
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+MethodCacheManager: 获取缓存方法失败 " + key + ": " + throwable.getMessage());
            return null;
        }
    }

    private void putString(String key, String value) {
        if (batchEditor != null) {
            batchEditor.putString(key, value);
        } else {
            preferences.edit().putString(key, value).apply();
        }
    }
}
