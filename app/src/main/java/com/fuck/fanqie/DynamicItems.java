package com.fuck.fanqie;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

/** 只保存业务类型、菜单标签及宿主版本；不保存原始响应或用户内容。 */
public final class DynamicItems {
    public static final String MINE = "mine";
    public static final String MINE_FEED_ID = "component/recommend_feed";
    public static final String RECOMMEND = "recommend";
    public static final String TOP_TABS = "top_tabs";
    public static final String SEARCH = "search_results";
    public static final String BANNERS = "bookshelf_banners";
    public static final String POPUPS = "popup_control";
    private static final String FILE = "fq_dynamic_items";
    private static final int LIMIT = 384;
    public static final class Item {
        public final String area, id, label;
        public final long firstVersion, lastVersion;
        private final boolean initialBlocked;
        Item(String area, String id, String label, long first, long last, boolean initial) {
            this.area = area; this.id = id; this.label = label;
            firstVersion = first; lastVersion = last; initialBlocked = initial;
        }
        public String display() { return label + (lastVersion == hostVersion ? "" : "（历史）"); }
    }
    private static final Map<String, Item> items = new LinkedHashMap<>();
    private static final Map<String, Boolean> running = new HashMap<>();
    private static SharedPreferences preferences;
    private static Handler handler;
    private static long hostVersion, bannerMigrationVersion;
    private static boolean dirty, scheduled, migrating;
    private static boolean catalogWritable = true;
    private static final Runnable FLUSH = () -> {
        synchronized (DynamicItems.class) {
            scheduled = false;
            if (!dirty || !catalogWritable) return;
            try {
                JSONArray array = new JSONArray();
                for (Item item : items.values()) {
                    array.put(new JSONObject().put("area", item.area).put("id", item.id).put("label", item.label)
                            .put("first", item.firstVersion).put("last", item.lastVersion).put("initial", item.initialBlocked));
                }
                SharedPreferences.Editor editor = preferences.edit().putString("catalog", array.toString());
                if (!preferences.contains("migration_version")) editor.putLong("migration_version", hostVersion);
                if (!preferences.contains("banner_migration_version")) editor.putLong("banner_migration_version", bannerMigrationVersion);
                if (!editor.commit()) throw new IllegalStateException("目录写入失败");
                dirty = false;
            } catch (Throwable throwable) {
                XposedBridge.log("FQHook+Dynamic: 目录保存失败: " + throwable);
            }
        }
    };

    private DynamicItems() { }
    public static synchronized void initialize(Context context) {
        if (preferences != null) return;
        Context app = context.getApplicationContext();
        preferences = (app == null ? context : app).getSharedPreferences(FILE, Context.MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        // 仅第一次主进程会对明确旧语义迁移；之后新发现项一律不默认屏蔽。
        migrating = !preferences.contains("migration_version");
        try {
            hostVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            bannerMigrationVersion = preferences.getLong("banner_migration_version", hostVersion);
            if (!preferences.contains("banner_migration_version")) dirty = true;
            JSONArray array = new JSONArray(preferences.getString("catalog", "[]"));
            // 越界或单条损坏时仍加载后续有效条目，但本进程禁止改写目录原文。
            if (array.length() > LIMIT) catalogWritable = false;
            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject value = array.getJSONObject(i);
                    String area = value.getString("area"), id = value.getString("id");
                    String key = key(area, id);
                    if (!valid(area, id) || items.containsKey(key)) throw new IllegalArgumentException("项目身份无效/重复");
                    Item item = new Item(area, id, safeLabel(value.getString("label"), id), value.getLong("first"),
                            value.getLong("last"), value.getBoolean("initial"));
                    items.put(key, item);
                    running.put(key, savedValue(key, item.initialBlocked));
                } catch (Throwable error) {
                    catalogWritable = false;
                    XposedBridge.log("FQHook+Dynamic: 目录第" + i + "项异常，原目录只读: " + error.getClass().getSimpleName());
                }
            }
        } catch (Throwable throwable) {
            catalogWritable = false;
            XposedBridge.log("FQHook+Dynamic: 目录加载异常，保留原文不覆盖: " + throwable);
        }
        // 已有独立功能并入我的页；一次继承原值，不受动态发现迁移窗口影响。
        String feedKey = key(MINE, MINE_FEED_ID);
        if (!items.containsKey(feedKey)) {
            boolean initial = PluginSettings.enabled("mine_feed");
            items.put(feedKey, new Item(MINE, MINE_FEED_ID, "推荐流", hostVersion, hostVersion, initial));
            running.put(feedKey, savedValue(feedKey, initial));
            dirty = catalogWritable;
        }
        observe(MINE, MINE_FEED_ID, "推荐流", false);
    }

    /** 必须在修改源列表/返回值之前调用；重复观测不触发重复磁盘写入。 */
    public static synchronized boolean observe(String area, String id, String label, boolean legacyBlocked) {
        if (preferences == null || !valid(area, id)) return false;
        String key = key(area, id);
        Item old = items.get(key);
        if (old == null && items.size() >= LIMIT) {
            XposedBridge.log("FQHook+Dynamic: 目录达到上限，未知项放行");
            return false;
        }
        // 初次目录迁移只对当前版本遇到的明确旧项目继承规则；此后新项目一律放行。
        boolean initial = old != null ? old.initialBlocked
                : catalogWritable && legacyBlocked
                && (migrating || (BANNERS.equals(area) && bannerMigrationVersion == hostVersion))
                && !Boolean.TRUE.equals(preferences.getAll().get("customized_" + area));
        if (old == null) {
            items.put(key, new Item(area, id, safeLabel(label, id), hostVersion, hostVersion, initial));
            running.put(key, savedValue(key, initial));
            XposedBridge.log("FQHook+Dynamic: 发现项目 " + area + "/" + id);
            dirty = true;
        } else if (old.lastVersion != hostVersion || !old.label.equals(safeLabel(label, id))) {
            items.put(key, new Item(area, id, safeLabel(label, id), old.firstVersion, hostVersion, initial));
            dirty = true;
        }
        if (dirty && catalogWritable && !scheduled) {
            scheduled = true;
            handler.postDelayed(FLUSH, 750);
        }
        Boolean blocked = running.get(key);
        return Boolean.TRUE.equals(blocked);
    }
    public static synchronized boolean anyBlocked(String area) {
        for (Map.Entry<String, Boolean> entry : running.entrySet()) {
            if (entry.getKey().startsWith(area + "/") && Boolean.TRUE.equals(entry.getValue())) return true;
        }
        return false;
    }
    public static synchronized boolean blocked(String area, String id) {
        return Boolean.TRUE.equals(running.get(key(area, id)));
    }
    public static synchronized List<Item> list(String area) {
        List<Item> result = new ArrayList<>();
        for (Item item : items.values()) if (item.area.equals(area)) result.add(item);
        Collections.sort(result, (left, right) -> Boolean.compare(
                left.lastVersion != hostVersion, right.lastVersion != hostVersion));
        return Collections.unmodifiableList(result);
    }
    public static synchronized boolean selected(Item item) {
        return savedValue(key(item.area, item.id), item.initialBlocked);
    }
    public static synchronized void save(String area, List<Item> shown, Set<String> blocked) {
        if (!validArea(area)) throw new IllegalArgumentException("未知区域");
        SharedPreferences.Editor editor = preferences.edit().putBoolean("customized_" + area, true);
        Set<String> displayed = new HashSet<>();
        for (Item item : shown) {
            if (!area.equals(item.area) || !items.containsKey(key(area, item.id))) throw new IllegalArgumentException("无效项目");
            displayed.add(item.id);
            editor.putBoolean("blocked:" + key(area, item.id), blocked.contains(item.id));
        }
        if (!displayed.containsAll(blocked)) throw new IllegalArgumentException("未展示的屏蔽项目");
        // 只保存打开选择框时的项目；不覆盖并发发现的项目或其他区域选择。
        if (!editor.commit()) throw new IllegalStateException("动态选项保存失败");
    }
    public static synchronized boolean pending() {
        if (preferences == null) return false;
        for (Item item : items.values()) {
            if (selected(item) != Boolean.TRUE.equals(running.get(key(item.area, item.id)))) return true;
        }
        return false;
    }
    private static boolean savedValue(String key, boolean fallback) {
        Object value = preferences.getAll().get("blocked:" + key);
        if (value != null && !(value instanceof Boolean)) {
            XposedBridge.log("FQHook+Dynamic: 屏蔽值类型异常，放行: " + key);
            return false;
        }
        return value == null ? fallback : (Boolean) value;
    }
    private static boolean valid(String area, String id) {
        return validArea(area) && id != null
                && id.length() <= 180 && id.matches("[A-Za-z0-9_:/.-]+");
    }
    private static boolean validArea(String area) {
        return MINE.equals(area) || RECOMMEND.equals(area) || TOP_TABS.equals(area) || SEARCH.equals(area)
                || BANNERS.equals(area) || POPUPS.equals(area)
                // 只兼容保留已安装动态版的历史目录，避免整份目录被判损坏；无展示或Hook消费者。
                || "search_entry".equals(area);
    }
    /** 只刷新目录元数据；失败不撤销独立保存的屏蔽选择，也不阻止宿主重启。 */
    public static synchronized boolean flushPending() {
        if (preferences == null || !dirty) return true;
        if (!catalogWritable) return false;
        handler.removeCallbacks(FLUSH);
        FLUSH.run();
        return !dirty;
    }
    private static String key(String area, String id) { return area + "/" + id; }
    private static String safeLabel(String label, String fallback) {
        return label == null || label.trim().isEmpty() || label.length() > 64 ? fallback : label.trim();
    }
}
