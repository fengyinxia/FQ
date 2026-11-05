package com.fuck.fanqie.hooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

import de.robv.android.xposed.XposedHelpers;

final class RecommendFilterHelper {
    private final ClassLoader hostClassLoader;

    RecommendFilterHelper(ClassLoader hostClassLoader) {
        this.hostClassLoader = hostClassLoader;
    }

    String getFilterReason(Object cellViewData, Set<String> allowedGroupTypes) {
        String showType = resolveName(readField(cellViewData, "showType"));
        if (readField(cellViewData, "bookHungerData") != null
                || (showType != null && showType.contains("BookHunger"))) {
            return "bookHunger/showType=" + showType;
        }
        if (showType != null && (showType.startsWith("Listen") || showType.startsWith("Audio")
                || showType.startsWith("FmUnlimited"))) {
            return "audioShowType=" + showType;
        }
        String groupType = resolveGroupTypeName(cellViewData);
        if ("ShuhuangEntrance".equals(groupType)) {
            return "bookHungerGroup=" + groupType;
        }
        if ("Audio".equals(groupType) || "BatchAudio".equals(groupType)) {
            return "audioGroup=" + groupType;
        }
        if (allowedGroupTypes != null && groupType != null && !allowedGroupTypes.contains(groupType)) {
            return "groupType=" + groupType;
        }
        if (isAudioLikeCell(cellViewData)) {
            return "audioLike=" + groupType;
        }
        return null;
    }

    String filterTree(Object cell, Set<String> allowed) {
        return filterTree(cell, allowed, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), 0);
    }

    private String filterTree(Object cell, Set<String> allowed, Set<Object> visited, int depth) {
        if (cell == null || depth > 8 || !visited.add(cell)) {
            return null;
        }
        String explicit = getFilterReason(cell, null);
        if (explicit != null) {
            return explicit;
        }
        Object nested = readField(cell, "cellData");
        if (nested instanceof List<?> && !((List<?>) nested).isEmpty()) {
            List<?> original = (List<?>) nested;
            List<Object> kept = new ArrayList<>();
            for (Object child : original) {
                String reason = filterTree(child, allowed, visited, depth + 1);
                if (reason == null) {
                    kept.add(child);
                } else {
                    XposedBridge.log("FQHook+RecommendFlow: 已过滤嵌套卡片 " + reason);
                }
            }
            if (kept.size() != original.size()) {
                XposedHelpers.setObjectField(cell, "cellData", kept);
                if (kept.isEmpty()) {
                    return "emptyNested/showType=" + resolveName(readField(cell, "showType"));
                }
            }
            // 混合流/分页容器本身不套用叶子 group 白名单，避免连同小说整段删除。
            return null;
        }
        return getFilterReason(cell, allowed);
    }

    String describe(Object cell) {
        List<String> books = new ArrayList<>();
        Object data = readField(cell, "bookData");
        if (data instanceof List<?>) {
            for (Object book : (List<?>) data) {
                String summary = "type=" + asString(readField(book, "bookType"))
                        + "/audio=" + asString(readField(book, "audioIconControl"))
                        + "/genre=" + asString(readField(book, "genreType"));
                if (!books.contains(summary) && books.size() < 4) {
                    books.add(summary);
                }
            }
        }
        Object nested = readField(cell, "cellData");
        List<String> childTypes = new ArrayList<>();
        if (nested instanceof List<?>) {
            for (Object child : (List<?>) nested) {
                String type = resolveGroupTypeName(child) + "/" + resolveName(readField(child, "showType"));
                if (!childTypes.contains(type) && childTypes.size() < 10) {
                    childTypes.add(type);
                }
            }
        }
        return "children=" + childTypes + ",group=" + resolveGroupTypeName(cell) + ",show=" + resolveName(readField(cell, "showType"))
                + ",bookType=" + resolveName(readField(cell, "bookType")) + ",books=" + books;
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
