package com.fuck.fanqie.hooks;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** 搜索专属消费入口；不 Hook 全局 RPC、DTO 构造器或书城解析器。 */
public final class SearchResultHooks {
    private final CachedTargets targets;
    private final java.util.Set<String> observed = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    public SearchResultHooks(CachedTargets targets) { this.targets = targets; }

    public void apply() {
        // 过滤后首屏可能始终填不满。禁止宿主无用户动作地连续拉完整个搜索结果集；
        // 不改hasMore/游标，用户滑动或点击加载更多仍走原分页。
        install(HookTargets.KEY_SEARCH_AUTO_FILL_METHOD, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (com.fuck.fanqie.DynamicItems.anyBlocked(com.fuck.fanqie.DynamicItems.SEARCH)) {
                    param.setResult(null);
                }
            }
        });
        // 响应检查器覆盖网络/缓存首屏及多种搜索分页消费者；成功后才处理。
        install(HookTargets.KEY_SEARCH_RESPONSE_METHOD, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || param.args[0] == null) return;
                try {
                    Object response = param.args[0];
                    Object value = SearchResultFilter.read(response, "searchTabs");
                    if (!(value instanceof List<?>)) return;
                    List<?> tabs = (List<?>) value;
                    SearchResultFilter.observeTabs(tabs);
                    List<Object> filtered = new ArrayList<>();
                    boolean changed = false;
                    for (Object tab : tabs) {
                        Object data = SearchResultFilter.read(tab, "data");
                        Object result = tab;
                        if (data instanceof List<?> && observed.size() < 32) {
                            java.util.Set<String> types = new java.util.LinkedHashSet<>();
                            for (Object cell : (List<?>) data) {
                                types.add(SearchResultFilter.name(SearchResultFilter.read(cell, "showType")));
                                if (types.size() >= 12) break;
                            }
                            String summary = SearchResultFilter.name(SearchResultFilter.read(tab, "tabType"))
                                    + " next=" + SearchResultFilter.read(tab, "nextOffset") + " " + types;
                            if (observed.add(summary)) XposedBridge.log("FQHook+SearchFilter: 类型 " + summary);
                        }
                        if (data instanceof List<?>) {
                            List<?> kept = SearchResultFilter.filter((List<?>) data);
                            if (kept != data) {
                                result = SearchResultFilter.copy(tab);
                                SearchResultFilter.put(result, "data", kept);
                                XposedBridge.log("FQHook+SearchFilter: 结果 "
                                        + SearchResultFilter.name(SearchResultFilter.read(tab, "tabType"))
                                        + " " + ((List<?>) data).size() + "→" + kept.size());
                                changed = true;
                            }
                        }
                        filtered.add(result);
                    }
                    // 只替换消费响应的列表；不改各Tab的游标/hasMore/passback。
                    if (changed) SearchResultFilter.put(response, "searchTabs", filtered);
                } catch (Throwable error) { HookUtils.logError("FQHook+SearchFilter: 响应过滤失败: ", error); }
            }
        });
        install(HookTargets.KEY_SEARCH_TABS_METHOD, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object response = param.args[4];
                    Object value = SearchResultFilter.read(response, "searchTabs");
                    if (!(value instanceof List<?>)) return;
                    List<?> tabs = (List<?>) value;
                    SearchResultFilter.observeTabs(tabs);
                    Object selectedValue = SearchResultFilter.read(response, "selectedTabIdx");
                    int selected = selectedValue instanceof Number ? ((Number) selectedValue).intValue() : 0;
                    int mapped = -1, common = -1;
                    List<Object> kept = new ArrayList<>();
                    for (int i = 0; i < tabs.size(); i++) {
                        Object tab = tabs.get(i);
                        String type = SearchResultFilter.name(SearchResultFilter.read(tab, "tabType"));
                        if (SearchResultFilter.blockedTab(type)) continue;
                        if (i == selected) mapped = kept.size();
                        if ("Common".equals(type)) common = kept.size();
                        kept.add(tab);
                    }
                    if (kept.size() == tabs.size()) return;
                    if (kept.isEmpty()) {
                        XposedBridge.log("FQHook+SearchFilter: 无可用搜索Tab，保留宿主原响应");
                        return;
                    }
                    Object copy = SearchResultFilter.copy(response);
                    SearchResultFilter.put(copy, "searchTabs", kept);
                    SearchResultFilter.put(copy, "selectedTabIdx", mapped >= 0 ? mapped : common >= 0 ? common : 0);
                    param.args[4] = copy;
                    XposedBridge.log("FQHook+SearchFilter: 顶栏 " + tabs.size() + "→" + kept.size());
                } catch (Throwable error) { HookUtils.logError("FQHook+SearchFilter: 顶栏过滤失败: ", error); }
            }
        });
        // 首屏/分页共用的搜索原始卡片转换器，也覆盖内部递归解析的混合卡。
        install(HookTargets.KEY_SEARCH_PARSE_METHOD, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof List<?>)) return;
                try { param.args[0] = SearchResultFilter.filter((List<?>) param.args[0]); }
                catch (Throwable error) { HookUtils.logError("FQHook+SearchFilter: 卡片过滤失败: ", error); }
                if (com.fuck.fanqie.PluginSettings.enabled("original_search")
                        || com.fuck.fanqie.PluginSettings.enabled("cover_title")) {
                    try {
                        param.args[0] = OriginalBookTitles.cells((List<?>) param.args[0],
                                com.fuck.fanqie.PluginSettings.enabled("original_search"));
                    } catch (Throwable error) {
                        HookUtils.logError("FQHook+OriginalTitle: 搜索展示副本处理失败，保留宿主: ", error);
                    }
                }
            }
        });
    }

    private void install(String key, XC_MethodHook hook) {
        try {
            Method method = targets.method(key);
            if (method == null) {
                XposedBridge.log("FQHook+SearchFilter: 目标缺失 " + key);
                return;
            }
            XposedBridge.hookMethod(method, hook);
            XposedBridge.log("FQHook+SearchFilter: 已安装 " + key + " " + method);
        } catch (Throwable error) { HookUtils.logError("FQHook+SearchFilter: 安装失败 " + key + ": ", error); }
    }
}
