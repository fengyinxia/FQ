package com.fuck.fanqie.cache;

import java.util.HashMap;
import java.util.HashSet;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public final class CachedTargets {
    private final TargetRepository targetRepository;
    private final Map<String, Method> methods = new HashMap<String, Method>();
    private final Map<String, Class<?>> types = new HashMap<String, Class<?>>();
    private final Set<String> missingMethods = new HashSet<String>();
    private final Set<String> missingTypes = new HashSet<String>();
    private long snapshotGeneration;

    public CachedTargets(TargetRepository targetRepository) {
        this.targetRepository = targetRepository;
        this.snapshotGeneration = targetRepository.getSnapshotGeneration();
    }

    public synchronized Method method(String key) {
        resetIfSnapshotChanged();
        if (key == null) {
            return null;
        }
        Method cachedMethod = methods.get(key);
        if (cachedMethod != null) {
            return cachedMethod;
        }
        if (missingMethods.contains(key)) {
            return null;
        }
        Method resolvedMethod = targetRepository.getMethod(key);
        if (resolvedMethod == null) {
            missingMethods.add(key);
            return null;
        }
        methods.put(key, resolvedMethod);
        return resolvedMethod;
    }

    public synchronized Class<?> type(String key) {
        resetIfSnapshotChanged();
        if (key == null) {
            return null;
        }
        Class<?> cachedType = types.get(key);
        if (cachedType != null) {
            return cachedType;
        }
        if (missingTypes.contains(key)) {
            return null;
        }
        Class<?> resolvedType = targetRepository.getClass(key);
        if (resolvedType == null) {
            missingTypes.add(key);
            return null;
        }
        types.put(key, resolvedType);
        return resolvedType;
    }

    private void resetIfSnapshotChanged() {
        long currentGeneration = targetRepository.getSnapshotGeneration();
        if (currentGeneration == snapshotGeneration) {
            return;
        }
        methods.clear();
        types.clear();
        missingMethods.clear();
        missingTypes.clear();
        snapshotGeneration = currentGeneration;
    }
}
