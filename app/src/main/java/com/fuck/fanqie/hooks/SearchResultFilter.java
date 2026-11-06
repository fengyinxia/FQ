package com.fuck.fanqie.hooks;

import com.fuck.fanqie.DynamicItems;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** 只处理搜索响应副本；页面 tabType 不是综合结果内每条内容的归属。 */
final class SearchResultFilter {
    private SearchResultFilter() { }

    static String name(Object value) {
        return value instanceof Enum<?> ? ((Enum<?>) value).name() : value == null ? null : value.toString();
    }

    static Object read(Object value, String field) {
        if (value == null) return null;
        try { return value.getClass().getField(field).get(value); }
        catch (NoSuchFieldException e) { return null; }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    static void put(Object value, String field, Object data) {
        try { value.getClass().getField(field).set(value, data); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    static Object copy(Object source) {
        try {
            Object result = source.getClass().getDeclaredConstructor().newInstance();
            for (Field field : source.getClass().getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    field.set(result, field.get(source));
                }
            }
            return result;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("搜索模型无法复制", e); }
    }

    static boolean protectedTab(String type) {
        return type == null || "Common".equals(type) || "None".equals(type);
    }

    static boolean blockedTab(String type) {
        return !protectedTab(type) && DynamicItems.blocked(DynamicItems.SEARCH, "tab/" + type);
    }

    private static boolean blocked(String category) {
        if (category == null) return false;
        if (blockedTab(category)) return true;
        // 当前宿主把“社区”顶栏标为 Topic，内容却使用多种 Community/Forum 模板。
        if ("Community".equals(category) || "BookComment".equals(category)) return blockedTab("Topic");
        if ("ShortPlay".equals(category)) {
            return blockedTab("ShortPlayOnly") || blockedTab("ShortPlaySingle");
        }
        if ("MotionComicOnly".equals(category)) return blockedTab("DynamicComic");
        return false;
    }

    static void observeTabs(List<?> tabs) {
        for (Object tab : tabs) {
            String type = name(read(tab, "tabType"));
            if (!protectedTab(type)) {
                Object title = read(tab, "title");
                DynamicItems.observe(DynamicItems.SEARCH, "tab/" + type,
                        title instanceof String ? (String) title : type, false);
            }
        }
    }

    static List<?> filter(List<?> source) {
        return filter(source, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()),
                new IdentityHashMap<Object, Object>(), 0);
    }

    private static List<?> filter(List<?> source, Set<Object> active, IdentityHashMap<Object, Object> decisions, int depth) {
        List<Object> kept = new ArrayList<>();
        boolean changed = false;
        for (Object item : source) {
            Object result = cell(item, active, decisions, depth);
            if (result != null) kept.add(result);
            if (result != item) changed = true;
        }
        return changed ? kept : source;
    }

    private static Object cell(Object source, Set<Object> active, IdentityHashMap<Object, Object> decisions, int depth) {
        if (source == null || depth > 8) return source;
        if (decisions.containsKey(source)) return decisions.get(source);
        if (!active.add(source)) return source;
        try {
            Object result = source;
            Object nested = read(source, "cellData");
            if (nested instanceof List<?> && !((List<?>) nested).isEmpty()) {
                List<?> original = (List<?>) nested;
                List<?> kept = filter(original, active, decisions, depth + 1);
                if (kept != original) {
                    if (kept.isEmpty()) { decisions.put(source, null); return null; }
                    result = copy(source);
                    put(result, "cellData", kept);
                }
                // 混合容器归属不能覆盖子项；不因一个短剧子项删除整组小说。
                decisions.put(source, result);
                return result;
            }
            String show = name(read(source, "showType"));
            String group = name(read(source, "groupIdType"));
            // 同一短剧展示模板可能承载真人短剧或漫剧，先看实际视频内容类型。
            for (String field : new String[]{"videoData", "searchVideoData", "bookData", "searchBookData", "novelAggregationCardItems"}) {
                // 短剧/社区等卡片附带的原著不是独立书籍搜索结果，不能按书籍选择拆坏它。
                if (!primary(field, show)) continue;
                Object value = read(result, field);
                if (!(value instanceof List<?>) || ((List<?>) value).isEmpty()) continue;
                List<?> list = (List<?>) value;
                List<Object> kept = new ArrayList<>();
                for (Object item : list) {
                    String category = field.equals("novelAggregationCardItems")
                            ? aggregationCategory(item) : field.contains("Video") || field.equals("videoData")
                            ? videoCategory(item) : bookCategory(item);
                    if (!blocked(category)) kept.add(item);
                }
                if (kept.size() != list.size()) {
                    if (result == source) result = copy(source);
                    put(result, field, kept);
                }
            }
            if (result != source) {
                boolean hasContent = false;
                for (String f : new String[]{"videoData", "searchVideoData", "bookData", "searchBookData", "novelAggregationCardItems"}) {
                    Object v = read(result, f);
                    if (primary(f, show) && v instanceof List<?> && !((List<?>) v).isEmpty()) hasContent = true;
                }
                if (!hasContent) result = null;
            }
            if (result != null && blocked(category(result, show, group))) result = null;
            decisions.put(source, result);
            return result;
        } finally { active.remove(source); }
    }

    private static boolean primary(String field, String show) {
        return !(field.equals("bookData") || field.equals("searchBookData")) || show == null
                || show.startsWith("SearchOneBook") || show.startsWith("SearchDoubleColBook")
                || show.startsWith("SearchComicDisplay") || show.equals("SearchBookFromAiSearchRecommend")
                || show.equals("BookList") || show.equals("HorizontalOne") || show.equals("VerticalOne");
    }

    private static String aggregationCategory(Object item) {
        Object video = read(item, "videoData");
        if (video != null) return videoCategory(video);
        return bookCategory(read(item, "bookData"));
    }

    private static String videoCategory(Object video) {
        Object old = read(video, "oldVideoData");
        if (old != null) video = old;
        String type = name(read(video, "contentType"));
        if ("MotionComic".equals(type) || "UnrealMotionComic".equals(type)) return "MotionComicOnly";
        if ("ShortPlay".equals(type) || "ShortSeriesPlay".equals(type) || "UnrealShortPlay".equals(type)) return "ShortPlay";
        if ("Movie".equals(type) || "TelePlay".equals(type)) return "LongVideo";
        if ("PUGC".equals(type) || "UgcPostVideo".equals(type)) return "PugcVideo";
        return null;
    }

    private static String bookCategory(Object book) {
        String type = name(read(book, "bookType"));
        if (type == null) return null;
        try {
            Class<?> utils = Class.forName("com.dragon.read.util.BookUtils", false, book.getClass().getClassLoader());
            if (Boolean.TRUE.equals(utils.getMethod("isComicType", String.class).invoke(null, type))) return "Comic";
            if (Boolean.TRUE.equals(utils.getMethod("isListenType", String.class).invoke(null, type))) return "Voice";
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("搜索书籍类型契约异常", e); }
        if ("0".equals(type) || "Read".equals(type)) return "Book";
        return null;
    }

    private static String category(Object cell, String show, String group) {
        // 优先采用明确内容元数据，避免把漫剧按 SearchShortPlay 模板误归入真人短剧。
        for (String field : new String[]{"videoData", "searchVideoData"}) {
            Object data = read(cell, field);
            if (data instanceof List<?> && !((List<?>) data).isEmpty()) {
                String category = null;
                for (Object item : (List<?>) data) {
                    String next = videoCategory(item);
                    if (next == null || category != null && !category.equals(next)) return null;
                    category = next;
                }
                if (category != null) return category;
            }
        }
        if (show == null) return null;
        if (show.equals("SearchContent") || show.equals("AISearchVerticalContentList")) return "Content";
        if (show.equals("SearchCommonProductRecommend") || show.equals("BuyBookTabNewer")
                || show.equals("EcomSearchBookstoreSelectInfo")) return "ECommerceBook";
        if (show.startsWith("ECommerceSearch") || show.startsWith("EcommerceSearch")) return "ECommerce";
        if (show.startsWith("SearchComicDisplay")) return "Comic";
        if (show.startsWith("HgComicSearchSeriesCell")) return "MotionComicOnly";
        if (show.startsWith("SearchShortPlay") || show.startsWith("SearchShortplay")
                || show.startsWith("SearchSubscribe") || show.equals("SearchSeriesCommentCell")) return "ShortPlay";
        if (show.startsWith("SearchCommunity") || show.equals("SearchForumPost") || show.equals("SearchForumTopic")) return "Community";
        if (show.equals("Author") || show.equals("SearchUserDisplayRecommend")) return "User";
        if (show.startsWith("SearchTopic") || show.equals("TopicSearch")) return "Topic";
        if (show.equals("SearchBookCommentCell")) return "BookComment";
        if (show.startsWith("SearchShortStory")) return "ShortStory";
        if (show.equals("SearchBookShelf")) return "Bookshelf";
        if (show.startsWith("SearchPicturePost")) return "PugcVideo";
        if ("Audio".equals(group) || "Listen".equals(name(read(cell, "bookType")))) return "Voice";
        if (show.equals("SearchOneBook") || show.equals("SearchDoubleColBook") || show.equals("SearchOneBookEnhanced")
                || show.equals("SearchOneBookAggregation") || show.equals("SearchBookFromAiSearchRecommend")) {
            Object books = read(cell, "bookData");
            if (books instanceof List<?> && !((List<?>) books).isEmpty()) return bookCategory(((List<?>) books).get(0));
        }
        return null;
    }
}
