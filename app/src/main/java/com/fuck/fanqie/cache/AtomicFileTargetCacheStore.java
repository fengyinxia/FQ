package com.fuck.fanqie.cache;

import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

public final class AtomicFileTargetCacheStore implements TargetCacheStore {
    private static final String TAG = "FQHook+TargetCacheStore";
    private static final String KEY_SCHEMA_VERSION = "schemaVersion";
    private static final String KEY_HOST_VERSION = "hostVersion";
    private static final String KEY_MODULE_VERSION = "moduleVersion";
    private static final String KEY_MODULE_FINGERPRINT = "moduleFingerprint";
    private static final String KEY_ENTRIES = "entries";
    private static final String KEY_KEY = "key";
    private static final String KEY_KIND = "kind";
    private static final String KEY_SERIALIZED = "serialized";

    private final AtomicFile atomicFile;

    public AtomicFileTargetCacheStore(File file) {
        this.atomicFile = new AtomicFile(file);
    }

    @Override
    public CacheSnapshot load() {
        File baseFile = atomicFile.getBaseFile();
        if (!baseFile.exists()) {
            return CacheSnapshot.empty();
        }

        FileInputStream inputStream = null;
        try {
            inputStream = atomicFile.openRead();
            byte[] bytes = readFully(inputStream);
            String json = new String(bytes, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(json);
            return parseSnapshot(root);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": 读取快照失败: " + throwable.getMessage());
            return CacheSnapshot.empty();
        } finally {
            closeQuietly(inputStream);
        }
    }

    @Override
    public boolean save(CacheSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }

        FileOutputStream outputStream = null;
        try {
            ensureParentDirectory();
            byte[] data = buildSnapshotJson(snapshot).toString().getBytes(StandardCharsets.UTF_8);
            outputStream = atomicFile.startWrite();
            outputStream.write(data);
            outputStream.flush();
            atomicFile.finishWrite(outputStream);
            return true;
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": 写入快照失败: " + throwable.getMessage());
            if (outputStream != null) {
                atomicFile.failWrite(outputStream);
            }
            return false;
        }
    }

    @Override
    public void clear() {
        atomicFile.delete();
    }

    private CacheSnapshot parseSnapshot(JSONObject root) {
        int schemaVersion = root.optInt(KEY_SCHEMA_VERSION, 0);
        if (schemaVersion != CacheSnapshot.CURRENT_SCHEMA_VERSION) {
            XposedBridge.log(TAG + ": 快照 schema 不匹配: " + schemaVersion);
            return CacheSnapshot.empty();
        }

        JSONObject entriesJson = root.optJSONObject(KEY_ENTRIES);
        Map<String, TargetEntry> entries = new LinkedHashMap<String, TargetEntry>();
        if (entriesJson != null) {
            Iterator<String> iterator = entriesJson.keys();
            while (iterator.hasNext()) {
                String entryKey = iterator.next();
                JSONObject entryJson = entriesJson.optJSONObject(entryKey);
                if (entryJson == null) {
                    continue;
                }
                TargetEntry entry = parseEntry(entryKey, entryJson);
                if (entry != null) {
                    entries.put(entry.key, entry);
                }
            }
        }

        return new CacheSnapshot(
                schemaVersion,
                root.optLong(KEY_HOST_VERSION, 0L),
                root.optInt(KEY_MODULE_VERSION, 0),
                root.optString(KEY_MODULE_FINGERPRINT, ""),
                entries
        );
    }

    private TargetEntry parseEntry(String defaultKey, JSONObject entryJson) {
        String key = entryJson.optString(KEY_KEY, defaultKey);
        String kindValue = entryJson.optString(KEY_KIND, "");
        String serialized = entryJson.optString(KEY_SERIALIZED, null);
        if (key == null || key.length() == 0 || serialized == null || serialized.length() == 0) {
            return null;
        }
        try {
            TargetEntry.Kind kind = TargetEntry.Kind.valueOf(kindValue);
            return new TargetEntry(key, kind, serialized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private JSONObject buildSnapshotJson(CacheSnapshot snapshot) throws Exception {
        JSONObject root = new JSONObject();
        root.put(KEY_SCHEMA_VERSION, snapshot.schemaVersion);
        root.put(KEY_HOST_VERSION, snapshot.hostVersion);
        root.put(KEY_MODULE_VERSION, snapshot.moduleVersion);
        root.put(KEY_MODULE_FINGERPRINT, snapshot.moduleFingerprint);

        JSONObject entriesJson = new JSONObject();
        for (TargetEntry entry : snapshot.entries()) {
            JSONObject entryJson = new JSONObject();
            entryJson.put(KEY_KEY, entry.key);
            entryJson.put(KEY_KIND, entry.kind.name());
            entryJson.put(KEY_SERIALIZED, entry.serialized);
            entriesJson.put(entry.key, entryJson);
        }
        root.put(KEY_ENTRIES, entriesJson);
        return root;
    }

    private void ensureParentDirectory() {
        File parent = atomicFile.getBaseFile().getParentFile();
        if (parent == null || parent.exists()) {
            return;
        }
        if (!parent.mkdirs() && !parent.exists()) {
            throw new IllegalStateException("无法创建缓存目录: " + parent.getAbsolutePath());
        }
    }

    private byte[] readFully(FileInputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toByteArray();
    }

    private void closeQuietly(FileInputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (Throwable ignored) {
        }
    }
}
