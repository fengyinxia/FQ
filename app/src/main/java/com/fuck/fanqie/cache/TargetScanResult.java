package com.fuck.fanqie.cache;

import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

public final class TargetScanResult {
    public interface Progress { void log(String message); }
    private final LinkedHashMap<String, TargetEntry> entries = new LinkedHashMap<String, TargetEntry>();
    private final java.util.LinkedHashSet<String> failures = new java.util.LinkedHashSet<>();
    private final Progress progress;

    public TargetScanResult() { this(null); }
    public TargetScanResult(Progress progress) { this.progress = progress; }
    public void report(String message) { if (progress != null) progress.log(message); }
    public void fail(String message) { failures.add(message); report("[失败] " + message); }
    public java.util.List<String> failures() { return new java.util.ArrayList<>(failures); }
    public void finish(String[] expected) {
        for (String key : expected) if (!entries.containsKey(key)) fail("未找到目标：" + key);
    }

    public void putMethod(String key, MethodData methodData) {
        if (key == null || methodData == null) {
            return;
        }
        entries.put(key, TargetEntry.forMethod(key, methodData.toDexMethod().serialize()));
        report("[成功] " + key);
    }

    public void putClass(String key, ClassData classData) {
        if (key == null || classData == null) {
            return;
        }
        entries.put(key, TargetEntry.forClass(key, classData.toDexType().serialize()));
        report("[成功] " + key);
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
