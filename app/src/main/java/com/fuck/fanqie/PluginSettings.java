package com.fuck.fanqie;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 用户配置与 DexKit 目标快照独立；运行配置只在进程初始化时读取一次。 */
public final class PluginSettings {
    public static final String FILE = "fq_plugin_settings";
    public static final class Option {
        public final String group, key, title;
        public final String[] keys, labels;
        private Option(String group, String key, String title, String[] keys, String[] labels) {
            this.group = group;
            this.key = key;
            this.title = title;
            this.keys = keys;
            this.labels = labels;
        }
        public boolean dynamic() { return "recommend".equals(key) || "mine".equals(key) || "top_tabs".equals(key) || DynamicItems.SEARCH.equals(key); }
        public boolean navigation() { return "bottom".equals(key); }
        public boolean multiple() { return navigation() || dynamic() || keys.length > 0; }
    }

    public static final List<Option> OPTIONS;
    private static final Set<String> KEYS;
    private static SharedPreferences preferences;
    private static Map<String, Boolean> running;
    private static final Set<String> invalidKeys = new LinkedHashSet<>();
    static {
        List<Option> list = new ArrayList<>();
        single(list, "内容屏蔽", "ads", "广告拦截");
        single(list, "内容屏蔽", "banner", "书架短剧 Banner");
        single(list, "内容屏蔽", "popup", "运营弹窗");
        multi(list, "内容屏蔽", "recommend", "推荐流精简", new String[0], new String[0]);
        multi(list, "内容屏蔽", DynamicItems.SEARCH, "搜索结果过滤", new String[0], new String[0]);
        multi(list, "UI 净化", "mine", "我的页", new String[0], new String[0]);
        multi(list, "UI 净化", "search", "搜索净化",
                new String[]{"search_mine", "search_words", "search_page"},
                new String[]{"隐藏我的页搜索入口", "清理搜索推荐词", "隐藏搜索页热榜与推荐入口"});
        multi(list, "UI 净化", "content", "内容净化",
                new String[]{"cover_title", "cover_comment", "chapter"},
                new String[]{"封面使用完整书名文字", "隐藏封面热门评论（含 EPUB）", "隐藏章末社区区域（作者说、讨论、礼物）"});
        single(list, "UI 净化", "red_dot", "隐藏红点提示");
        multi(list, "UI 净化", "top_tabs", "顶部频道精简", new String[0], new String[0]);
        single(list, "UI 净化", "sidebar", "侧栏入口改为设置");
        single(list, "拓展", "download", "下载与导出");
        multi(list, "拓展", "bottom", "底栏自定义", new String[0], new String[0]);
        single(list, "拓展", "reader_back", "阅读器返回行为");
        single(list, "拓展", "update", "屏蔽宿主更新检查");
        single(list, "拓展", "ab", "阻止 AB 配置更新");
        multi(list, "拓展", "report", "上报控制",
                new String[]{"report_behavior", "report_crash"}, new String[]{"拦截点击与行为上报", "拦截崩溃上报"});
        single(list, "拓展", "push", "阻止推送进程");
        single(list, "拓展", "forest", "拦截 Forest 后台任务");
        OPTIONS = Collections.unmodifiableList(list);
        Set<String> keys = new LinkedHashSet<>();
        for (Option option : OPTIONS) {
            if (option.multiple()) Collections.addAll(keys, option.keys);
            else keys.add(option.key);
        }
        // 旧键只用于迁移；不再作为固定多选项展示。
        Collections.addAll(keys, "rec_audio", "rec_hunger", "rec_video", "rec_comic", "rec_other",
                "mine_wallet", "mine_vip", "mine_quick", "top_tabs",
                "bottom_category", "bottom_hide_video", "bottom_order", "startup", "lucky", "mine_feed");
        KEYS = Collections.unmodifiableSet(keys);
    }

    private PluginSettings() { }
    private static void single(List<Option> list, String group, String key, String title) {
        list.add(new Option(group, key, title, new String[0], new String[0]));
    }
    private static void multi(List<Option> list, String group, String key, String title, String[] keys, String[] labels) {
        list.add(new Option(group, key, title, keys, labels));
    }

    public static synchronized void initialize(Context context) {
        initialize(context, true);
    }
    public static synchronized void initialize(Context context, boolean discoverItems) {
        if (running != null) return;
        Context application = context.getApplicationContext();
        preferences = (application == null ? context : application).getSharedPreferences(FILE, Context.MODE_PRIVATE);
        running = Collections.unmodifiableMap(read());
        if (discoverItems) {
            try { DynamicItems.initialize(context); }
            catch (Throwable throwable) {
                de.robv.android.xposed.XposedBridge.log("FQHook+Settings: 动态目录初始化异常，保留设置入口: " + throwable);
            }
            try { NavigationSettings.initialize(context); }
            catch (Throwable throwable) {
                de.robv.android.xposed.XposedBridge.log("FQHook+Navigation: 配置读取异常，保留宿主: " + throwable);
            }
        }
        de.robv.android.xposed.XposedBridge.log("FQHook+Settings: 本次运行配置 " + running);
    }
    private static Map<String, Boolean> read() {
        Map<String, ?> stored = preferences.getAll();
        Map<String, Boolean> result = new HashMap<>();
        for (String key : KEYS) {
            Object value = stored.get(key);
            if (value != null && !(value instanceof Boolean)) {
                if (invalidKeys.add(key)) de.robv.android.xposed.XposedBridge.log(
                        "FQHook+Settings: 单项配置类型不兼容，暂不干预宿主: " + key);
                result.put(key, false); // 不覆盖原值；用户在设置页保存该项即可修复。
                continue;
            }
            invalidKeys.remove(key);
            // 缺项维持既有行为；单项损坏不影响其他配置或恢复入口。
            result.put(key, value == null || (Boolean) value);
        }
        return result;
    }
    public static synchronized boolean enabled(String key) {
        checkKey(key);
        if (running == null) throw new IllegalStateException("插件配置未初始化");
        return running.get(key);
    }
    public static synchronized Map<String, Boolean> saved() {
        if (preferences == null) throw new IllegalStateException("插件配置未初始化");
        return Collections.unmodifiableMap(read());
    }
    public static synchronized void save(Map<String, Boolean> changes) {
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            checkKey(change.getKey());
            if (change.getValue() == null) throw new IllegalArgumentException("配置值为空");
            editor.putBoolean(change.getKey(), change.getValue());
        }
        if (!editor.commit()) throw new IllegalStateException("配置写入失败");
    }
    public static synchronized boolean pending() {
        return !read().equals(running) || DynamicItems.pending() || NavigationSettings.pending();
    }
    public static Option option(String key) {
        for (Option option : OPTIONS) if (option.key.equals(key)) return option;
        throw new IllegalArgumentException("未知设置项: " + key);
    }
    public static boolean any(String... keys) {
        for (String key : keys) if (enabled(key)) return true;
        return false;
    }
    private static void checkKey(String key) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("未知配置: " + key);
    }
}
