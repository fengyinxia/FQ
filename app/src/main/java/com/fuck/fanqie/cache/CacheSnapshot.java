package com.fuck.fanqie.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CacheSnapshot {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public final int schemaVersion;
    public final long hostVersion;
    public final int moduleVersion;
    public final String moduleFingerprint;
    private final Map<String, TargetEntry> entries;

    public CacheSnapshot(int schemaVersion, long hostVersion, int moduleVersion, String moduleFingerprint,
                         Map<String, TargetEntry> entries) {
        this.schemaVersion = schemaVersion;
        this.hostVersion = hostVersion;
        this.moduleVersion = moduleVersion;
        this.moduleFingerprint = moduleFingerprint == null ? "" : moduleFingerprint;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<String, TargetEntry>(entries));
    }

    public static CacheSnapshot empty() {
        return new CacheSnapshot(CURRENT_SCHEMA_VERSION, 0L, 0, "", Collections.<String, TargetEntry>emptyMap());
    }

    public Collection<TargetEntry> entries() {
        return entries.values();
    }

    public TargetEntry entry(String key) {
        return entries.get(key);
    }

    public int size() {
        return entries.size();
    }
}
