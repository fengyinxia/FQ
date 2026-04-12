package com.fuck.fanqie.hooks;

import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XposedHelpers;

final class RecommendFilterHelper {
    private final ClassLoader hostClassLoader;

    RecommendFilterHelper(ClassLoader hostClassLoader) {
        this.hostClassLoader = hostClassLoader;
    }

    String getFilterReason(Object cellViewData, Set<String> allowedGroupTypes) {
        String groupType = resolveGroupTypeName(cellViewData);
        if (groupType != null && !allowedGroupTypes.contains(groupType)) {
            return "groupType=" + groupType;
        }
        if (isAudioLikeCell(cellViewData)) {
            return "audioLike=" + groupType;
        }
        return null;
    }

    private boolean isAudioLikeCell(Object cellViewData) {
        if (cellViewData == null) {
            return false;
        }
        if (isListenBookType(readField(cellViewData, "bookType"))) {
            return true;
        }
        Object bookData = readField(cellViewData, "bookData");
        if (!(bookData instanceof List<?>)) {
            return false;
        }
        for (Object item : (List<?>) bookData) {
            if (item == null) {
                continue;
            }
            String bookType = asString(readField(item, "bookType"));
            if (isListenBookType(bookType)) {
                return true;
            }
            if (isAudioIconShortStory(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAudioIconShortStory(Object bookData) {
        Object audioIconControl = readField(bookData, "audioIconControl");
        if (!Boolean.TRUE.equals(audioIconControl)) {
            return false;
        }
        String genreType = asString(readField(bookData, "genreType"));
        if (genreType == null || genreType.isEmpty()) {
            return false;
        }
        try {
            Class<?> bookUtilsClass = XposedHelpers.findClass("com.dragon.read.util.BookUtils", hostClassLoader);
            Object result = XposedHelpers.callStaticMethod(bookUtilsClass, "isShortStory", genreType);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isListenBookType(Object bookTypeValue) {
        if (bookTypeValue == null) {
            return false;
        }
        if (bookTypeValue instanceof Enum<?>) {
            return "Listen".equals(((Enum<?>) bookTypeValue).name());
        }
        String bookType = String.valueOf(bookTypeValue);
        if ("1".equals(bookType) || "Listen".equalsIgnoreCase(bookType)) {
            return true;
        }
        try {
            Class<?> nsCommonDependClass = XposedHelpers.findClass("com.dragon.read.NsCommonDepend", hostClassLoader);
            Object impl = XposedHelpers.getStaticObjectField(nsCommonDependClass, "IMPL");
            Object result = XposedHelpers.callMethod(impl, "isListenType", bookType);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String resolveGroupTypeName(Object cellViewData) {
        Object groupType = readField(cellViewData, "groupIdType");
        return resolveName(groupType);
    }

    private String resolveName(Object value) {
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        return value == null ? null : String.valueOf(value);
    }

    private Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(target, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
