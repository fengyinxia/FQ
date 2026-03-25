package com.fuck.fanqie.cache;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.fuck.fanqie.BuildConfig;

import org.luckypray.dexkit.wrap.DexClass;
import org.luckypray.dexkit.wrap.DexMethod;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

public class TargetRepository {
    private static final String MODULE_FINGERPRINT_FALLBACK_PREFIX = "version:";
    private static final String SNAPSHOT_FILE_NAME = "target-cache-snapshot.json";

    private final ClassLoader hostClassLoader;
    private final TargetCacheStore cacheStore;
    private final int currentModuleVersionCode;
    private final String currentModuleFingerprint;
    private CacheSnapshot snapshot;
    private long snapshotGeneration = 1L;

    public TargetRepository(Context context, ClassLoader hostClassLoader) {
        this.hostClassLoader = hostClassLoader;
        this.currentModuleVersionCode = BuildConfig.VERSION_CODE;
        this.currentModuleFingerprint = buildCurrentModuleFingerprint(context);
        this.cacheStore = new AtomicFileTargetCacheStore(new File(context.getFilesDir(), SNAPSHOT_FILE_NAME));
        this.snapshot = cacheStore.load();
    }

    public void clearAll() {
        cacheStore.clear();
        snapshot = CacheSnapshot.empty();
        snapshotGeneration++;
        XposedBridge.log("FQHook+TargetRepository: 已清空所有缓存");
    }

    public long getCachedVersionCode() {
        return snapshot.hostVersion;
    }

    public int getCachedModuleVersionCode() {
        return snapshot.moduleVersion;
    }

    public int getCurrentModuleVersionCode() {
        return currentModuleVersionCode;
    }

    public String getCachedModuleFingerprint() {
        return snapshot.moduleFingerprint;
    }

    public String getCurrentModuleFingerprint() {
        return currentModuleFingerprint;
    }

    public long getSnapshotGeneration() {
        return snapshotGeneration;
    }

    public boolean publishFreshScan(long versionCode, TargetScanResult scanResult) {
        if (scanResult == null || scanResult.isEmpty()) {
            XposedBridge.log("FQHook+TargetRepository: 扫描结果为空，取消发布");
            return false;
        }

        CacheSnapshot freshSnapshot = new CacheSnapshot(
                CacheSnapshot.CURRENT_SCHEMA_VERSION,
                versionCode,
                getCurrentModuleVersionCode(),
                getCurrentModuleFingerprint(),
                toEntryMap(scanResult)
        );
        boolean success = cacheStore.save(freshSnapshot);
        if (success) {
            snapshot = freshSnapshot;
            snapshotGeneration++;
            XposedBridge.log("FQHook+TargetRepository: 已发布完整扫描结果, hostVersion="
                    + versionCode + ", moduleVersion=" + getCurrentModuleVersionCode()
                    + ", entryCount=" + freshSnapshot.size());
        } else {
            XposedBridge.log("FQHook+TargetRepository: 发布扫描结果失败");
        }
        return success;
    }

    public Class<?> getClass(String key) {
        TargetEntry entry = snapshot.entry(key);
        if (entry == null || entry.kind != TargetEntry.Kind.CLASS) {
            return null;
        }
        try {
            return new DexClass(entry.serialized).getInstance(hostClassLoader);
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+TargetRepository: 获取缓存类失败 " + key + ": " + throwable.getMessage());
            return null;
        }
    }

    public Method getMethod(String key) {
        TargetEntry entry = snapshot.entry(key);
        if (entry == null || entry.kind != TargetEntry.Kind.METHOD) {
            return null;
        }
        try {
            return new DexMethod(entry.serialized).getMethodInstance(hostClassLoader);
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+TargetRepository: 获取缓存方法失败 " + key + ": " + throwable.getMessage());
            return null;
        }
    }

    private Map<String, TargetEntry> toEntryMap(TargetScanResult scanResult) {
        Map<String, TargetEntry> entries = new LinkedHashMap<String, TargetEntry>();
        for (TargetEntry entry : scanResult.entries()) {
            entries.put(entry.key, entry);
        }
        return entries;
    }

    private String buildCurrentModuleFingerprint(Context context) {
        File apkFile = resolveModuleApkFile(context);
        if (apkFile == null || !apkFile.exists()) {
            return MODULE_FINGERPRINT_FALLBACK_PREFIX + currentModuleVersionCode;
        }
        return currentModuleVersionCode + ":" + apkFile.length() + ":" + apkFile.lastModified();
    }

    private File resolveModuleApkFile(Context context) {
        File codeSourceFile = resolveModuleApkFromCodeSource();
        if (codeSourceFile != null && codeSourceFile.exists()) {
            return codeSourceFile;
        }
        File packageFile = resolveModuleApkFromPackageManager(context);
        if (packageFile != null && packageFile.exists()) {
            return packageFile;
        }
        return null;
    }

    private File resolveModuleApkFromCodeSource() {
        try {
            if (TargetRepository.class.getProtectionDomain() == null
                    || TargetRepository.class.getProtectionDomain().getCodeSource() == null
                    || TargetRepository.class.getProtectionDomain().getCodeSource().getLocation() == null) {
                return null;
            }
            String path = TargetRepository.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            if (path == null || path.length() == 0) {
                return null;
            }
            return new File(URLDecoder.decode(path, "UTF-8"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private File resolveModuleApkFromPackageManager(Context context) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
            if (applicationInfo == null || applicationInfo.sourceDir == null || applicationInfo.sourceDir.length() == 0) {
                return null;
            }
            return new File(applicationInfo.sourceDir);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
