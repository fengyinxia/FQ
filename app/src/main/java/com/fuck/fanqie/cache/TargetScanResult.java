package com.fuck.fanqie.cache;

import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

public final class TargetScanResult {
    private final LinkedHashMap<String, TargetEntry> entries = new LinkedHashMap<String, TargetEntry>();

    public void putMethod(String key, MethodData methodData) {
        if (key == null || methodData == null) {
            return;
        }
        entries.put(key, TargetEntry.forMethod(key, methodData.toDexMethod().serialize()));
    }

    public void putClass(String key, ClassData classData) {
        if (key == null || classData == null) {
            return;
        }
        entries.put(key, TargetEntry.forClass(key, classData.toDexType().serialize()));
    }

    public Collection<TargetEntry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }
}
