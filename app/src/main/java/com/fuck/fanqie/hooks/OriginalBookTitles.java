package com.fuck.fanqie.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 封面、推荐及书架共用原名观察；不改原响应、不请求详情、不把书名落盘。 */
final class OriginalBookTitles {
    private static final String API_BOOK = "com.dragon.read.rpc.model.ApiBookInfo";
    private static final String CELL = "com.dragon.read.rpc.model.CellViewData";
    private static final Map<String, String> NAMES = new LinkedHashMap<String, String>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 512; }
    };
    private static final Set<String> TITLE_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "bookName", "book_name", "bookShortName", "book_short_name", "title", "cellName", "cell_name"));

    private OriginalBookTitles() { }

    static String original(Object book) throws ReflectiveOperationException {
        if (book == null || !API_BOOK.equals(book.getClass().getName())) return null;
        Object type = read(book, "bookType");
        if (!"0".equals(type) && !"Read".equals(type)) return null;
        String id = text(read(book, "bookId"));
        String name = text(read(book, "originalBookName"));
        if (id == null || name == null) return null;
        synchronized (NAMES) { NAMES.put(id, name); }
        return name;
    }

    static String known(String id) {
        synchronized (NAMES) { return NAMES.get(text(id)); }
    }

    static String bookshelf(Object model) throws ReflectiveOperationException {
        if (model == null) return null;
        Object type = model.getClass().getMethod("getBookType").invoke(model);
        if (!(type instanceof Enum<?>) || !"READ".equalsIgnoreCase(((Enum<?>) type).name())) return null;
        Object id = model.getClass().getMethod("getBookId").invoke(model);
        return id instanceof String ? known((String) id) : null;
    }

    static Object cover(Object info) throws ReflectiveOperationException {
        if (info == null) return null;
        Object result = copy(info);
        String name;
        synchronized (NAMES) { name = NAMES.get(text(read(info, "bookId"))); }
        if (name != null) put(result, "bookName", name);
        // 沿用旧封面文字模式；未知原名时保留当前 bookName，绝不冒充已恢复原名。
        put(result, "bookShortName", null);
        return result;
    }

    static Object cell(Object value, boolean replace) throws ReflectiveOperationException {
        return cell(value, replace, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    static List<?> cells(List<?> source, boolean replace) throws ReflectiveOperationException {
        return cells(source, replace, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static List<?> cells(List<?> source, boolean replace, Set<Object> active, int depth)
            throws ReflectiveOperationException {
        if (depth > 8) return source;
        List<Object> result = new ArrayList<>(source.size());
        boolean changed = false;
        for (Object item : source) {
            Object next = cell(item, replace, active, depth + 1);
            result.add(next);
            changed |= next != item;
        }
        return changed ? result : source;
    }

    private static Object cell(Object source, boolean replace, Set<Object> active, int depth)
            throws ReflectiveOperationException {
        if (source == null || depth > 8 || !active.add(source)) return source;
        try {
            if (API_BOOK.equals(source.getClass().getName())) {
                String name = original(source);
                if (name == null && "0".equals(read(source, "bookType"))) {
                    name = known(text(read(source, "bookId")));
                }
                if (!replace || name == null) return source;
                Object result = copy(source);
                put(result, "bookName", name);
                // 宿主部分卡片直接取短名（包含空串），不能将其置空。
                put(result, "bookShortName", name);
                // 部分卡片直接使用 title；只改与原展示书名相同的标题，不动推广介绍文案。
                Object title = read(source, "title");
                if (title != null && (title.equals(read(source, "bookName"))
                        || title.equals(read(source, "bookShortName")))) put(result, "title", name);
                cleanHighlight(result, source);
                return result;
            }
            if ("com.dragon.read.rpc.model.BookGroupData".equals(source.getClass().getName())) {
                Object books = read(source, "bookList");
                if (!(books instanceof List<?>)) return source;
                List<?> next = cells((List<?>) books, replace, active, depth + 1);
                if (next == books) return source;
                Object result = copy(source);
                put(result, "bookList", next);
                return result;
            }
            if (!CELL.equals(source.getClass().getName())) return source;
            String show = enumName(read(source, "showType"));
            String group = enumName(read(source, "groupIdType"));
            // 原著挂靠短剧/漫画/听书/社区卡不参与小说标题替换。
            if (excluded(show) || excluded(group)) return source;
            Object result = source;
            for (String field : new String[]{"cellData", "bookData", "bookGroupList"}) {
                Object items = read(source, field);
                if (!(items instanceof List<?>)) continue;
                List<?> next = cells((List<?>) items, replace, active, depth + 1);
                if (next != items) {
                    if (result == source) result = copy(source);
                    put(result, field, next);
                    // 搜索Provider可能优先取 CellViewData.searchHighLight[title]，须同时去掉旧书名高亮。
                    if ("bookData".equals(field)) cleanHighlight(result, source);
                }
            }
            Object ranked = read(source, "rankBookInfo");
            if (ranked != null) {
                Object next = cell(ranked, replace, active, depth + 1);
                if (next != ranked) {
                    if (result == source) result = copy(source);
                    put(result, "rankBookInfo", next);
                }
            }
            String id = text(read(source, "bookId"));
            // 只改能按 bookId 对上本卡单书的 cellName，不能把合集/作者卡标题改成书名。
            String name = replace ? matchingName(source, id) : null;
            if (name != null && (show != null && (show.startsWith("SearchOneBook")
                    || show.startsWith("SearchDoubleColBook")) || "Book".equals(group))) {
                if (result == source) result = copy(source);
                put(result, "cellName", name);
                cleanHighlight(result, source);
            }
            if (replace) {
                Object config = dynamicTitle(source, group);
                if (config != null) {
                    if (result == source) result = copy(source);
                    put(result, "dynamicStyleConfig", config);
                }
            }
            return result;
        } finally { active.remove(source); }
    }

    private static Object dynamicTitle(Object cell, String group) throws ReflectiveOperationException {
        // 只覆盖已实机确认的单书动态模板；不碰合集、推广标题、事件和跳转参数。
        if (!"Book".equals(group)) return null;
        Object config = read(cell, "dynamicStyleConfig");
        Object raw = read(config, "dynamicResultConfig");
        Object books = read(cell, "bookData");
        if (!(raw instanceof Map<?, ?>) || !(books instanceof List<?>) || ((List<?>) books).size() != 1) return null;
        Map<?, ?> values = (Map<?, ?>) raw;
        String id = text(values.get("#group_id"));
        if (id == null || !values.containsKey("#title_0")) return null;
        Object book = ((List<?>) books).get(0);
        if (book == null || !API_BOOK.equals(book.getClass().getName()) || !id.equals(read(book, "bookId"))) return null;
        String name = original(book);
        if (name == null || name.equals(values.get("#title_0"))) return null;
        Map<Object, Object> next = new LinkedHashMap<>((Map<?, ?>) values);
        next.put("#title_0", name);
        Object result = copy(config);
        put(result, "dynamicResultConfig", next);
        return result;
    }

    private static String matchingName(Object cell, String id) throws ReflectiveOperationException {
        if (id == null) return null;
        for (String field : new String[]{"bookData", "searchBookData"}) {
            Object value = read(cell, field);
            if (!(value instanceof List<?>)) continue;
            for (Object book : (List<?>) value) {
                if (book != null && API_BOOK.equals(book.getClass().getName()) && id.equals(read(book, "bookId"))) {
                    return original(book);
                }
            }
        }
        return null;
    }

    private static boolean excluded(String value) {
        if (value == null) return false;
        return value.contains("Video") || value.contains("Comic") || value.contains("Audio")
                || value.contains("Listen") || value.contains("Community") || value.contains("Forum")
                || value.contains("Topic") || value.contains("Author");
    }

    private static void cleanHighlight(Object result, Object source) throws ReflectiveOperationException {
        Object value = read(source, "searchHighLight");
        if (!(value instanceof Map<?, ?>)) return;
        Map<Object, Object> kept = new LinkedHashMap<>((Map<?, ?>) value);
        kept.keySet().removeIf(key -> TITLE_KEYS.contains(String.valueOf(key)));
        put(result, "searchHighLight", kept);
    }

    static Object read(Object value, String name) throws ReflectiveOperationException {
        if (value == null) return null;
        try { return value.getClass().getField(name).get(value); }
        catch (NoSuchFieldException absent) { return null; }
    }

    private static String enumName(Object value) { return value instanceof Enum<?> ? ((Enum<?>) value).name() : null; }
    private static String text(Object value) {
        if (!(value instanceof String)) return null;
        String result = ((String) value).trim();
        return result.isEmpty() || result.length() > 512 ? null : result;
    }
    private static void put(Object target, String name, Object value) throws ReflectiveOperationException {
        target.getClass().getField(name).set(target, value);
    }
    private static Object copy(Object source) throws ReflectiveOperationException {
        Object result = source.getClass().getDeclaredConstructor().newInstance();
        for (Field field : source.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                field.set(result, field.get(source));
            }
        }
        return result;
    }
}
