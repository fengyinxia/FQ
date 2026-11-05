package com.fuck.fanqie;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import de.robv.android.xposed.XposedBridge;

/** 底栏显示、排列和主页原子保存；不存下标，不参与DexKit目标缓存。 */
public final class NavigationSettings {
    public static final class Layout {
        public final List<String> order;
        public final Set<String> hidden;
        public final String home;
        public Layout(List<String> order, Set<String> hidden, String home) {
            this.order = Collections.unmodifiableList(new ArrayList<>(order));
            this.hidden = Collections.unmodifiableSet(new HashSet<>(hidden));
            this.home = home;
        }
    }
    private static SharedPreferences prefs;
    private static Layout running;
    private static boolean compatible = true;
    private static final Set<String> available = new LinkedHashSet<>();
    private NavigationSettings() { }

    public static synchronized void initialize(Context context) {
        if (prefs != null) return;
        prefs = context.getSharedPreferences("fq_navigation", Context.MODE_PRIVATE);
        try {
            if (prefs.contains("layout")) running = decode(prefs.getString("layout", ""));
        } catch (RuntimeException error) {
            compatible = false;
            throw error; // 不覆盖损坏配置，也不自动执行旧迁移。
        }
    }

    /** 必须传入过滤前、经宿主构建器确认支持的路由，不枚举并强开所有枚举常量。 */
    public static synchronized void discover(List<String> routes, List<String> original) {
        if (!compatible) throw new IllegalStateException("底栏配置不兼容，未覆盖原配置");
        if (prefs == null || routes.isEmpty()) return;
        for (String route : routes) {
            if (!route.matches("[A-Za-z0-9_]{1,80}")) throw new IllegalArgumentException("无效路由");
        }
        available.addAll(routes);
        if (running == null) {
            List<String> order = new ArrayList<>(new LinkedHashSet<>(routes));
            if (PluginSettings.enabled("bottom_order")) {
                List<String> preferred = Arrays.asList("BookShelf", "BookStore", "BookCategory", "MyProfile",
                        "LuckyBenefit", "VideoSeriesFeedTab", "Novel", "Community", "ShopMall");
                Collections.sort(order, (a, b) -> Integer.compare(rank(preferred, a), rank(preferred, b)));
            }
            Set<String> hidden = new HashSet<>();
            for (String id : order) {
                if (!original.contains(id)) hidden.add(id);
            }
            if (PluginSettings.enabled("bottom_category")) hidden.remove("BookCategory");
            if (PluginSettings.enabled("bottom_hide_video")) hidden.add("VideoSeriesFeedTab");
            if (PluginSettings.enabled("lucky")) hidden.add("LuckyBenefit");
            String home = PluginSettings.enabled("startup") && order.contains("BookShelf") ? "BookShelf" : "";
            if (!home.isEmpty()) hidden.remove(home);
            if (visible(order, hidden).isEmpty()) hidden.remove(order.get(0));
            if (home.isEmpty()) home = visible(order, hidden).get(0);
            Layout migrated = new Layout(order, hidden, home);
            persist(migrated); // 失败不发布半迁移运行配置。
            running = migrated;
            XposedBridge.log("FQHook+Navigation: 已迁移底栏与主页 " + order + ", home=" + home);
        } else {
            Layout saved = saved();
            Layout merged = merge(saved, routes);
            if (!same(saved, merged)) persist(merged);
            running = merge(running, routes); // 仅追加新发现项，不热应用用户编辑。
        }
    }
    private static Layout merge(Layout layout, List<String> routes) {
        List<String> order = new ArrayList<>(layout.order);
        for (String id : routes) if (!order.contains(id)) order.add(id);
        return new Layout(order, layout.hidden, layout.home);
    }
    public static synchronized Layout saved() {
        if (prefs == null || running == null) return new Layout(Collections.emptyList(), Collections.emptySet(), "");
        return decode(prefs.getString("layout", ""));
    }
    public static synchronized Set<String> available() { return new LinkedHashSet<>(available); }
    public static synchronized boolean visible(String id) {
        return running != null && running.order.contains(id) && !running.hidden.contains(id);
    }
    /** 读取本进程布局，不读取UI保存草稿；尚未发现路由时只延续旧迁移策略。 */
    public static synchronized boolean disableWelfare() {
        if (!compatible || prefs == null) return false;
        if (running != null && running.order.contains("LuckyBenefit")) {
            return running.hidden.contains("LuckyBenefit");
        }
        return PluginSettings.enabled("lucky");
    }
    public static synchronized List<String> runtimeOrder() {
        return running == null ? Collections.emptyList() : visible(running.order, running.hidden);
    }
    public static synchronized String runtimeHome(List<String> effectiveRoutes) {
        if (running == null || effectiveRoutes.isEmpty()) return null;
        return effectiveRoutes.contains(running.home) ? running.home : effectiveRoutes.get(0);
    }
    public static synchronized void save(Layout layout) {
        Layout current = saved();
        if (layout.order.size() != new HashSet<>(layout.order).size()
                || !new HashSet<>(layout.order).equals(new HashSet<>(current.order))
                || !layout.order.containsAll(layout.hidden)) throw new IllegalArgumentException("底栏项目已变化，请重开编辑");
        List<String> visible = visible(layout.order, layout.hidden);
        visible.retainAll(available);
        if (visible.isEmpty()) throw new IllegalArgumentException("至少显示一个可用 Tab");
        if (!visible.contains(layout.home)) throw new IllegalArgumentException("当前主页已隐藏，请重新选择主页");
        persist(layout);
    }
    public static synchronized boolean pending() {
        return compatible && running != null && !same(running, saved());
    }
    public static List<String> visible(List<String> order, Set<String> hidden) {
        List<String> result = new ArrayList<>();
        for (String id : order) if (!hidden.contains(id)) result.add(id);
        return result;
    }
    public static String label(String id) {
        switch (id) {
            case "BookShelf": return "书架";
            case "BookStore": return "书城";
            case "BookCategory": return "分类";
            case "MyProfile": return "我的";
            case "LuckyBenefit": return "赚钱";
            case "VideoSeriesFeedTab": return "短剧";
            case "Novel": return "小说";
            case "Community": return "社区";
            case "ShopMall": return "商城";
            default: return id;
        }
    }
    private static int rank(List<String> order, String id) {
        int index = order.indexOf(id);
        return index < 0 ? order.size() : index;
    }
    private static boolean same(Layout a, Layout b) {
        return a.order.equals(b.order) && a.hidden.equals(b.hidden) && a.home.equals(b.home);
    }
    private static void persist(Layout layout) {
        try {
            String value = new JSONObject().put("order", new JSONArray(layout.order))
                    .put("hidden", new JSONArray(layout.hidden)).put("home", layout.home).toString();
            if (!prefs.edit().putString("layout", value).commit()) throw new IllegalStateException("底栏配置写入失败");
        } catch (org.json.JSONException exception) { throw new IllegalStateException("底栏配置编码失败", exception); }
    }
    private static Layout decode(String value) {
        try {
            JSONObject object = new JSONObject(value);
            List<String> order = new ArrayList<>();
            Set<String> hidden = new HashSet<>();
            JSONArray array = object.getJSONArray("order");
            if (array.length() > 32) throw new IllegalArgumentException("底栏项目过多");
            for (int i = 0; i < array.length(); i++) {
                String id = array.getString(i);
                if (!id.matches("[A-Za-z0-9_]{1,80}") || order.contains(id)) throw new IllegalArgumentException("底栏身份不兼容");
                order.add(id);
            }
            array = object.getJSONArray("hidden");
            for (int i = 0; i < array.length(); i++) hidden.add(array.getString(i));
            String home = object.getString("home");
            if (order.isEmpty() || !order.containsAll(hidden) || hidden.contains(home) || !order.contains(home)) {
                throw new IllegalArgumentException("底栏主页配置不兼容");
            }
            return new Layout(order, hidden, home);
        } catch (org.json.JSONException exception) { throw new IllegalStateException("底栏配置读取失败", exception); }
    }
}
