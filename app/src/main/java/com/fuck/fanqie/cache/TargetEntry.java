package com.fuck.fanqie.cache;

public final class TargetEntry {
    public enum Kind {
        METHOD,
        CLASS
    }

    public final String key;
    public final Kind kind;
    public final String serialized;

    public TargetEntry(String key, Kind kind, String serialized) {
        this.key = key;
        this.kind = kind;
        this.serialized = serialized;
    }

    public static TargetEntry forMethod(String key, String serialized) {
        return new TargetEntry(key, Kind.METHOD, serialized);
    }

    public static TargetEntry forClass(String key, String serialized) {
        return new TargetEntry(key, Kind.CLASS, serialized);
    }
}
