package com.fuck.fanqie.cache;

public interface TargetCacheStore {
    CacheSnapshot load();

    boolean save(CacheSnapshot snapshot);

    void clear();
}
