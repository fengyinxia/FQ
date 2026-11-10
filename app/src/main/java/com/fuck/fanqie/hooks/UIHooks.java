package com.fuck.fanqie.hooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.PluginSettings;
import com.fuck.fanqie.DynamicItems;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class UIHooks extends BaseHook {
    private final CachedTargets cachedTargets;
    private final RecommendFilterHelper recommendFilterHelper;
    private final SearchCueWordFilter searchCueWordFilter = new SearchCueWordFilter();
    private final ThreadLocal<Integer> myPageCreationDepth = new ThreadLocal<>();

    public UIHooks(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        super(hostClassLoader);
        this.cachedTargets = cachedTargets;
        this.recommendFilterHelper = new RecommendFilterHelper(hostClassLoader);
    }

    @Override
    public void apply() {
        applyDisableMyPageSidebarHooks();
        applySlidingTabHooks();
        applyRedDotHooks();
        applyRemoveMyPageExtraCardHooks();
        applyMyPageVipEntranceHooks();
        applyMyPageSearchHooks();
        applyDisableMyPageRecommendHooks();
        applyMyPageWalletHooks();
        new KmpMyPageHooks(cachedTargets, hostClassLoader).apply();
        applyCustomVipHooks();
        applySearchWordHooks();
        applySearchBarHooks();
        applyRecommendFlowHooks();
    }

    private void applyMyPageWalletHooks() {
        installWalletComponentHook();
        if (!PluginSettings.enabled("sidebar")) return;
        try {
            Method method = cachedTargets.method(HookTargets.KEY_MY_PAGE_CONTENT_METHOD);
            if (method == null || !isPageContentFactory(method)) {
                XposedBridge.log("FQHook+MyPageWallet: 未找到兼容的我的页内容创建入口，跳过");
                return;
            }
            final Method settingsClick = cachedTargets.method(HookTargets.KEY_MY_PAGE_SETTINGS_CLICK_METHOD);
            MyPageViews.trackLayouts();
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    MyPageViews.beginContent();
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Integer layout = MyPageViews.finishContent(param.getResult());
                    if (!(param.getResult() instanceof View)) {
                        return;
                    }
                    final View root = (View) param.getResult();
                    // 仅在当前页面内按稳定图标识别两个按钮，不读混淆资源名。
                    final View[] header = MyPageViews.findHeader(root, layout);
                    final View.OnClickListener settingsListener = createMyPageSettingsListener(settingsClick, param.thisObject);
                    final int settingsIcon = root.getResources().getIdentifier(
                            "skin_mine_icon_setting_new_light", "drawable", "com.dragon.read");
                    if (settingsIcon == 0) {
                        XposedBridge.log("FQHook+MyPageHeader: 原生设置图标不存在，保留原菜单入口");
                    }
                    if (replaceMyPageMenuWithSettings(header, settingsListener, settingsIcon)) {
                        XposedBridge.log("FQHook+MyPageHeader: 已在原侧栏菜单位置替换设置图标与点击行为");
                    }
                    root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            replaceMyPageMenuWithSettings(header, settingsListener, settingsIcon);
                        }
                    });
                }
            });
            XposedBridge.log("FQHook+MyPageWallet: 已安装钱包福利卡隐藏及菜单替换设置入口 Hook");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageWallet: 隐藏我的页指定区域失败: ", throwable);
        }
    }

    private void installWalletComponentHook() {
        Class<?> walletClass = cachedTargets.type(HookTargets.KEY_MY_PAGE_WALLET_CLASS);
        if (walletClass == null) {
            XposedBridge.log("FQHook+MyPageWallet: 钱包卡组件缺失，跳过隐藏");
            return;
        }
        Field candidate = null;
        for (Field field : walletClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == View.class) {
                if (candidate != null) {
                    XposedBridge.log("FQHook+MyPageWallet: 钱包根 View 字段不唯一，跳过");
                    return;
                }
                candidate = field;
            }
        }
        if (candidate == null) {
            XposedBridge.log("FQHook+MyPageWallet: 钱包组件没有根 View 字段，跳过");
            return;
        }
        final Field rootField = candidate;
        rootField.setAccessible(true);
        final java.util.Set<View> observed = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
        XposedBridge.hookAllConstructors(walletClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                boolean mine = false;
                for (Object arg : param.args) {
                    if (arg != null && ("com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2".equals(arg.getClass().getName())
                            || "com.dragon.read.component.biz.impl.mine.KmpFanqieMineFragment".equals(arg.getClass().getName()))) {
                        mine = true;
                    }
                }
                if (!mine || param.hasThrowable()) {
                    return;
                }
                try {
                    Object value = rootField.get(param.thisObject);
                    if (!(value instanceof ViewGroup) || !observed.add((View) value)) {
                        return;
                    }
                    final View wallet = (View) value;
                    if (!DynamicItems.observe(DynamicItems.MINE, "component/wallet", "钱包与福利卡片",
                            PluginSettings.enabled("mine_wallet"))) return;
                    wallet.setVisibility(View.GONE);
                    wallet.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (wallet.getVisibility() != View.GONE) {
                                wallet.setVisibility(View.GONE);
                            }
                        }
                    });
                    XposedBridge.log("FQHook+MyPageWallet: 已隐藏我的页钱包福利组件整卡");
                } catch (Throwable throwable) {
                    HookUtils.logError("FQHook+MyPageWallet: 钱包根 View 读取失败，保留原行为: ", throwable);
                }
            }
        });
    }

    private static View.OnClickListener createMyPageSettingsListener(Method method, Object fragment) {
        if (method == null || !View.OnClickListener.class.isAssignableFrom(method.getDeclaringClass())) {
            XposedBridge.log("FQHook+MyPageHeader: 未找到兼容的设置点击回调，保留原菜单入口");
            return null;
        }
        try {
            // DexKit 定位到宿主原生监听器；复用其 openSetting 和页面来源信息。
            return (View.OnClickListener) XposedHelpers.newInstance(method.getDeclaringClass(), fragment);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageHeader: 创建原生设置回调失败，保留菜单: ", throwable);
            return null;
        }
    }

    private static boolean replaceMyPageMenuWithSettings(View[] header,
            View.OnClickListener listener, int iconId) {
        if (listener == null || iconId == 0 || header == null) {
            return false;
        }
        View menu = header[0];
        View settings = header[1];
        if (!(menu instanceof android.widget.ImageView) || !(settings instanceof android.widget.ImageView)) {
            return false;
        }
        // 保留菜单原布局参数和坐标；备用设置按钮仍为隐藏占位。
        menu.setOnClickListener(listener);
        ((android.widget.ImageView) menu).setImageResource(iconId);
        settings.setVisibility(View.INVISIBLE);
        menu.setVisibility(View.VISIBLE);
        return true;
    }

    private void applyDisableMyPageRecommendHooks() {
        if (!DynamicItems.observe(DynamicItems.MINE, DynamicItems.MINE_FEED_ID, "推荐流", false)) return;
        try {
            Method method = cachedTargets.method(HookTargets.KEY_MY_PAGE_RECOMMEND_ENABLE_METHOD);
            if (method == null) {
                XposedBridge.log("FQHook+MyPageRecommend: 未找到推荐流开关，跳过");
                return;
            }
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getParameterTypes().length != 0 || method.getReturnType() != boolean.class) {
                XposedBridge.log("FQHook+MyPageRecommend: 开关签名不兼容，跳过: " + method);
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                private boolean logged;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(Boolean.FALSE);
                    if (!logged) {
                        logged = true;
                        XposedBridge.log("FQHook+MyPageRecommend: 我的页推荐流开关已返回 false");
                    }
                }
            });
            XposedBridge.log("FQHook+MyPageRecommend: 已安装推荐流开关拦截: " + method);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageRecommend: 关闭推荐流失败: ", throwable);
        }
    }

    private void applyDisableMyPageSidebarHooks() {
        if (!PluginSettings.enabled("sidebar")) return;
        try {
            Class<?> configClass = XposedHelpers.findClass(
                    "com.dragon.read.base.ssconfig.template.GameRevisitPathV693Model",
                    hostClassLoader
            );
            // 7.3.9.32 的配置由静态单例持有，旧版 b() Getter 已不存在。
            boolean singletonPatched = false;
            try {
                Object singleton = XposedHelpers.getStaticObjectField(configClass, "b");
                if (singleton != null) {
                    XposedHelpers.setBooleanField(singleton, "sideBarEnable", false);
                    singletonPatched = true;
                }
            } catch (Throwable throwable) {
                XposedBridge.log("FQHook+MyPageSidebar: 无法修改当前配置单例: " + throwable);
            }
            int constructorCount = XposedBridge.hookAllConstructors(configClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedHelpers.setBooleanField(param.thisObject, "sideBarEnable", false);
                }
            }).size();
            int getterCount = XposedBridge.hookAllMethods(configClass, "b", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object config = param.getResult();
                    if (config != null) {
                        XposedHelpers.setBooleanField(config, "sideBarEnable", false);
                    }
                }
            }).size();
            XposedBridge.log("FQHook+MyPageSidebar: 配置已处理（单例=" + singletonPatched
                    + "，构造器=" + constructorCount + "，旧 Getter=" + getterCount + "）");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageSidebar: Hook侧边栏配置失败: ", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private void processTabData(Object target) {
        try {
            Object value = XposedHelpers.getObjectField(target, "bookMallTabDataList");
            if (!(value instanceof List)) {
                return;
            }
            List<Object> tabDataList = (List<Object>) value;
            if (tabDataList.isEmpty()) {
                return;
            }

            int selected = XposedHelpers.getIntField(target, "selectIndex");
            List<Object> filtered = new ArrayList<>();
            int newSelected = -1;
            for (int i = 0; i < tabDataList.size(); i++) {
                Object tab = tabDataList.get(i);
                boolean blocked = false;
                if (tab != null) {
                    // 使用宿主业务类型与书城ID，不用标题或列表下标当身份。
                    int type = XposedHelpers.getIntField(tab, "tabType");
                    long store = XposedHelpers.getLongField(tab, "bookStoreId");
                    String title = (String) XposedHelpers.getObjectField(tab, "tabName");
                    blocked = DynamicItems.observe(DynamicItems.TOP_TABS,
                            "type/" + type + "/store/" + store, title, false);
                }
                if (!blocked) {
                    if (i == selected) newSelected = filtered.size();
                    filtered.add(tab);
                }
            }
            if (filtered.size() == tabDataList.size()) return;
            // 宿主需要非空频道；全屏蔽时保留首项，不留下空白页。
            if (filtered.isEmpty()) filtered.add(tabDataList.get(0));
            XposedHelpers.setObjectField(target, "bookMallTabDataList", filtered);
            XposedHelpers.setIntField(target, "selectIndex", Math.max(0, newSelected));
            XposedBridge.log("FQHook-SlidingTab: 动态频道 " + tabDataList.size() + "→" + filtered.size());
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook-SlidingTab: 过滤逻辑失败: " + throwable);
        }
    }

    public void applyCustomVipHooks() {
        if (!PluginSettings.enabled("ads")) return;
        Class<?> vipInfoModelClass = cachedTargets.type(HookTargets.KEY_VIP_INFO_MODEL_CLASS);
        if (vipInfoModelClass == null) {
            return;
        }
        XposedBridge.hookAllConstructors(vipInfoModelClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object[] args = param.args;
                args[0] = "4102415999";
                args[1] = "1";
                args[2] = "10000";
                args[3] = Boolean.TRUE;
                args[4] = Boolean.TRUE;
                args[5] = 1;
                args[6] = Boolean.TRUE;
            }
        });
        XposedBridge.log("FQHook+applyHooks: 已自定义VIP信息");
    }

    public void applyMyPageVipEntranceHooks() {
        Method method = cachedTargets.method(HookTargets.KEY_MY_PAGE_VIP_ENTRANCE_METHOD);
        if (method != null) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable() && param.getResult() != null && MineItemFilter.observe("VIP", null)) {
                        param.setResult(null);
                    }
                }
            });
            XposedBridge.log("FQHook+applyHooks: 已安装我的页会员入口动态发现与过滤");
        }
    }

    public void applyMyPageSearchHooks() {
        if (!PluginSettings.enabled("search_mine")) return;
        disableMyPageDynamicSearch();
    }

    private void disableMyPageDynamicSearch() {
        Method method = cachedTargets.method(HookTargets.KEY_MY_PAGE_SEARCH_BAR_METHOD);
        if (method == null) {
            return;
        }
        try {
            if (isPageContentFactory(method)) {
                Method factory = cachedTargets.method(HookTargets.KEY_MY_PAGE_SEARCH_FACTORY_METHOD);
                if (factory == null || !View.class.isAssignableFrom(factory.getReturnType())) {
                    XposedBridge.log("FQHook+MyPageSearch: 搜索 View 工厂缺失，保留页面");
                    return;
                }
                // 动态作用域限定本页同步创建，不能影响书城复用的搜索工厂。
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Integer depth = myPageCreationDepth.get();
                        myPageCreationDepth.set(depth == null ? 1 : depth + 1);
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Integer depth = myPageCreationDepth.get();
                        if (depth == null || depth <= 1) {
                            myPageCreationDepth.remove();
                        } else {
                            myPageCreationDepth.set(depth - 1);
                        }
                    }
                });
                XposedBridge.hookMethod(factory, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (myPageCreationDepth.get() == null || !(param.getResult() instanceof View)) {
                            return;
                        }
                        final View search = (View) param.getResult();
                        search.setVisibility(View.INVISIBLE);
                        search.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                if (search.getVisibility() != View.INVISIBLE) {
                                    search.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
                        XposedBridge.log("FQHook+MyPageSearch: 已捕获并隐藏我的页搜索 View，保留占位");
                    }
                });
                XposedBridge.log("FQHook+MyPageSearch: 已安装我的页搜索控件局部隐藏 Hook");
            } else {
                // 旧宿主（≤7.1.x）：搜索入口是独立构建方法，整段替换即可。
                XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
                XposedBridge.log("FQHook+MyPageSearch: 已禁用动态搜索入口构建");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageSearch: 处理动态搜索入口失败: ", throwable);
        }
    }

    /** 整页内容工厂：onCreateContent，或 (LayoutInflater, ViewGroup, Bundle) -> View。 */
    private static boolean isPageContentFactory(Method method) {
        if ("onCreateContent".equals(method.getName())) {
            return true;
        }
        if (method.getReturnType() != View.class) {
            return false;
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        return paramTypes.length == 3
                && paramTypes[0] == LayoutInflater.class
                && paramTypes[1] == ViewGroup.class
                && paramTypes[2] == Bundle.class;
    }

    public void applyRedDotHooks() {
        new MineRedDotHooks(cachedTargets).apply();
    }

    public void applyRemoveMyPageExtraCardHooks() {
        applyQuickAccessCardFilter();
        applyQuickAccessAggregateFilter();
    }

    /** 按缓存的转换方法挂钩；仅按 QUICK_ACCESS 的 CardType 精确过滤。 */
    private void applyQuickAccessCardFilter() {
        try {
            Method converter = cachedTargets.method(HookTargets.KEY_QUICK_ACCESS_CONVERT_METHOD);
            if (!isQuickAccessFactory(converter, 4)
                    || !"kotlin.jvm.functions.Function1".equals(converter.getParameterTypes()[3].getName())) {
                XposedBridge.log("FQHook+RemoveCard: 未找到兼容的快捷功能转换方法，跳过");
                return;
            }
            XposedBridge.hookMethod(converter, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length != 4 || param.args[0] == null) {
                        return;
                    }
                    try {
                        Object card = param.args[0];
                        Object cardType = XposedHelpers.getObjectField(card, "a");
                        if (!(cardType instanceof Enum)) {
                            return;
                        }
                        String container = ((Enum<?>) cardType).name();
                        // 原生聚合器还会单独转换 COMMON：预约、点赞、小游戏等不在 QUICK_ACCESS 内。
                        if (!"QUICK_ACCESS".equals(container) && !"COMMON".equals(container)) {
                            return;
                        }
                        Object value = XposedHelpers.getObjectField(card, "b");
                        if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
                            return;
                        }
                        List<?> items = (List<?>) value;
                        List<Object> kept = new ArrayList<>();
                        for (Object item : items) {
                            if (item == null) {
                                kept.add(null);
                                continue;
                            }
                            Object featureType = XposedHelpers.getObjectField(item, "a");
                            if (!(featureType instanceof Enum)
                                    || !MineItemFilter.observe(((Enum<?>) featureType).name(), null)) {
                                kept.add(item);
                            }
                        }
                        XposedBridge.log("FQHook+RemoveCard: " + container + " 快捷功能候选=" + items.size()
                                + "，允许=" + kept.size());
                        if (kept.size() != items.size()) {
                            XposedHelpers.setObjectField(card, "b", kept);
                            XposedBridge.log("FQHook+RemoveCard: 已过滤新版快捷功能 "
                                    + (items.size() - kept.size()) + " 项");
                        }
                    } catch (Throwable throwable) {
                        HookUtils.logError("FQHook+RemoveCard: 功能列表过滤失败，保留原列表: ", throwable);
                    }
                }
            });
            XposedBridge.log("FQHook+RemoveCard: 已 Hook 快捷功能源列表: " + converter);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+RemoveCard: 新版快捷功能 Hook 失败: ", throwable);
        }
    }

    /** 新版还会将预约、点赞及 COMMON 卡片合并进快捷入口，须在合并后再次过滤。 */
    private void applyQuickAccessAggregateFilter() {
        try {
            Method aggregate = cachedTargets.method(HookTargets.KEY_QUICK_ACCESS_AGGREGATE_METHOD);
            if (!isQuickAccessFactory(aggregate, 3)) {
                XposedBridge.log("FQHook+RemoveCard: 未找到兼容的快捷功能聚合方法，跳过");
                return;
            }
            final java.util.Map<Class<?>, String> types = new java.util.HashMap<>();
            String[] keys = {HookTargets.KEY_QUICK_HISTORY_CLASS, HookTargets.KEY_QUICK_DOWNLOAD_CLASS,
                    HookTargets.KEY_QUICK_MESSAGE_CLASS, HookTargets.KEY_QUICK_FOLLOW_CLASS};
            String[] identities = {"READING_HISTORY", "BOOK_DOWNLOAD", "MY_MESSAGE", "MY_FOLLOW"};
            for (int i = 0; i < keys.length; i++) {
                Class<?> model = cachedTargets.type(keys[i]);
                if (model != null) types.put(model, identities[i]);
            }
            XposedBridge.hookMethod(aggregate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length != 3 || !(param.getResult() instanceof List)) {
                        return;
                    }
                    List<?> items = (List<?>) param.getResult();
                    if (items.isEmpty()) {
                        return;
                    }
                    List<Object> kept = new ArrayList<>();
                    for (Object item : items) {
                        if (item == null) {
                            continue;
                        }
                        String identity = types.get(item.getClass());
                        // 聚合DTO没有稳定业务码时保留，不能用混淆类名充当用户选择ID。
                        if (identity == null || !MineItemFilter.observe(identity, null)) kept.add(item);
                    }
                    if (kept.size() != items.size()) {
                        param.setResult(kept);
                        XposedBridge.log("FQHook+RemoveCard: 聚合列表候选=" + items.size()
                                + "，保留=" + kept.size());
                    }
                }
            });
            XposedBridge.log("FQHook+RemoveCard: 已 Hook 快捷功能聚合列表: " + aggregate);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+RemoveCard: 新版聚合列表 Hook 失败: ", throwable);
        }
    }

    private static boolean isQuickAccessFactory(Method method, int parameterCount) {
        return method != null && List.class.isAssignableFrom(method.getReturnType())
                && method.getParameterTypes().length == parameterCount
                && "androidx.fragment.app.FragmentActivity".equals(method.getParameterTypes()[1].getName());
    }

    public void applyRecommendFlowHooks() {
        installRecommendSceneHook(HookTargets.KEY_RECOMMEND_MALL_PARSE_METHOD, -1);
        installRecommendSceneHook(HookTargets.KEY_RECOMMEND_NATIVE_PARSE_METHOD, 3);
        installRecommendSceneHook(HookTargets.KEY_RECOMMEND_KMP_PARSE_METHOD, 4);
        // V 无业务来源参数；K/U 已递归处理书城数据，不能在共用底层再次无条件过滤。
        XposedBridge.log("FQHook+RecommendFlow: 已安装仅书城推荐的动态目录发现与过滤");
    }

    private void installRecommendSceneHook(String key, final int sceneIndex) {
        try {
            Method method = cachedTargets.method(key);
            if (method == null || !List.class.isAssignableFrom(method.getReturnType())
                    || method.getParameterTypes().length != (sceneIndex < 0 ? 5 : sceneIndex + 1)) {
                XposedBridge.log("FQHook+RecommendFlow: 场景解析目标缺失或不兼容 " + key);
                return;
            }
            final int recommendTabType;
            final int novelTabType;
            if (sceneIndex != 4) {
                Class<?> tabTypeClass = XposedHelpers.findClass(
                        "com.dragon.read.rpc.model.BookstoreTabType", hostClassLoader);
                Object type = XposedHelpers.getStaticObjectField(tabTypeClass, "recommend");
                recommendTabType = ((Number) XposedHelpers.callMethod(type, "getValue")).intValue();
                Object novelType = XposedHelpers.getStaticObjectField(tabTypeClass, "novel");
                novelTabType = ((Number) XposedHelpers.callMethod(novelType, "getValue")).intValue();
            } else {
                recommendTabType = -1;
                novelTabType = -1;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                private final Set<String> seen = java.util.Collections.synchronizedSet(new HashSet<String>());

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object scene = sceneIndex < 0 ? null : param.args[sceneIndex];
                    String name = scene instanceof Enum<?> ? ((Enum<?>) scene).name() : String.valueOf(scene);
                    if (seen.size() < 24 && seen.add("scene=" + name)) {
                        XposedBridge.log("FQHook+RecommendFlow: " + key + " 实际场景=" + name);
                    }
                    try {
                        // K/U 的第三参是 BookstoreTabType 业务值，不是页面索引。
                        // K 也被短剧详情复用；无场景时必须正向确认频道，不能全局放行 null。
                        boolean recommend = "BOOK_MALL_RECOMMEND".equals(name);
                        boolean novel = "BOOK_MALL_NOVEL".equals(name);
                        if (sceneIndex != 4 && (scene == null || "BOOK_MALL".equals(name))) {
                            boolean bookmall = !(sceneIndex < 0 && Boolean.TRUE.equals(param.args[4]));
                            Object tabType = param.args[2];
                            recommend = bookmall && tabType instanceof Number
                                    && recommendTabType == ((Number) tabType).intValue();
                            novel = bookmall && tabType instanceof Number
                                    && novelTabType == ((Number) tabType).intValue();
                        }
                        String source = "scene=" + name + (sceneIndex != 4 ? ",tabType=" + param.args[2] : "")
                                + ",filter=" + recommend;
                        if (seen.size() < 24 && seen.add(source)) {
                            XposedBridge.log("FQHook+RecommendFlow: 来源 " + key + " " + source);
                        }
                        Object cell = param.args[0];
                        if ((recommend || novel) && PluginSettings.any(
                                "original_recommend", "original_novel", "cover_title", "original_bookshelf")) {
                            try {
                                boolean replace = PluginSettings.enabled(recommend
                                        ? "original_recommend" : "original_novel");
                                cell = OriginalBookTitles.cell(cell, replace);
                                param.args[0] = cell;
                            } catch (Throwable error) {
                                HookUtils.logError("FQHook+OriginalTitle: 书城展示副本处理失败，保留原名规则前数据: ", error);
                            }
                        }
                        // 小说频道只替换书名，推荐净化仍仅作用于推荐频道。
                        if (!recommend) return;
                        String reason = recommendFilterHelper.filterTree(cell, true);
                        if (reason != null) {
                            param.setResult(new ArrayList<>());
                            XposedBridge.log("FQHook+RecommendFlow: 场景过滤 " + key + " " + reason);
                        } else if (seen.size() < 24) {
                            String summary = recommendFilterHelper.describe(cell);
                            if (seen.add(summary)) {
                                XposedBridge.log("FQHook+RecommendFlow: 推荐保留 " + summary);
                            }
                        }
                    } catch (Throwable throwable) {
                        HookUtils.logError("FQHook+RecommendFlow: 场景过滤失败，保留原行为: ", throwable);
                    }
                }

            });
            XposedBridge.log("FQHook+RecommendFlow: 已安装场景解析过滤 " + key + ": " + method);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+RecommendFlow: 场景 Hook 失败: ", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    public void applySearchBarHooks() {
        if (!PluginSettings.enabled("search_page")) return;
        final Set<String> filteredClassNames = new HashSet<>(Arrays.asList(
                "com.dragon.read.component.biz.impl.holder.HotSearchWordsHolder$HotWordsModel",
                "com.dragon.read.component.biz.impl.holder.middlepage.searchrank.model.SearchRankModel",
                "com.dragon.read.component.biz.impl.holder.SearchBookRobotEntranceHolder$SearchBookRobotEntranceModel"
        ));

        Method method = cachedTargets.method(HookTargets.KEY_SEARCH_BAR_METHOD);
        if (method == null) {
            return;
        }

        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    List<Object> items = (List<Object>) param.args[0];
                    if (items == null) {
                        return;
                    }
                    List<Object> filtered = new ArrayList<>();
                    for (Object item : items) {
                        if (item != null && !filteredClassNames.contains(item.getClass().getName())) {
                            filtered.add(item);
                        }
                    }
                    param.args[0] = filtered;
                }
            });
            XposedBridge.log("FQHook+applySearchBarHooks: 已成功hook搜索栏方法");
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+applySearchBarHooks: Hook搜索栏方法失败: " + throwable.getMessage());
        }
    }

    public void applySearchWordHooks() {
        if (!PluginSettings.enabled("search_words")) return;
        try {
            Class<?> searchCueWordExtendClass = XposedHelpers.findClass("com.dragon.read.search.SearchCueWordExtend", hostClassLoader);
            Class<?> searchCueWordClass = XposedHelpers.findClass("com.dragon.read.rpc.model.SearchCueWord", hostClassLoader);

            XposedHelpers.findAndHookConstructor(searchCueWordExtendClass, searchCueWordClass, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (searchCueWordFilter.clearWord(param.args[0])) {
                        XposedBridge.log("FQHook+SearchWord: 已清理构造路径的推荐热词展示字段");
                    }
                }
            });
            XposedBridge.log("FQHook+applySearchWordHooks: 已成功hook SearchCueWordExtend构造方法");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applySearchWordHooks: 构造路径 Hook 失败: ", throwable);
        }
        hookSearchCueDisplay();
        // 运行时接收列表再清理，覆盖 JSON、缓存及 KMP 转换，不依赖构造器命中。
        hookSearchCueList(HookTargets.KEY_SEARCH_CUE_LIST_METHOD, "原生");
        hookSearchCueList(HookTargets.KEY_SEARCH_CUE_KMP_LIST_METHOD, "KMP");
    }

    private void hookSearchCueDisplay() {
        try {
            Method method = cachedTargets.method(HookTargets.KEY_SEARCH_CUE_DISPLAY_METHOD);
            if (method == null) {
                XposedBridge.log("FQHook+SearchWord: 未找到搜索页提示词显示入口，跳过");
                return;
            }
            if (method.getReturnType() != void.class || method.getParameterTypes().length != 1
                    || !"com.dragon.read.rpc.model.SearchCueWord".equals(method.getParameterTypes()[0].getName())) {
                XposedBridge.log("FQHook+SearchWord: 搜索页提示词显示入口签名不兼容，跳过: " + method);
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object word = searchCueWordFilter.displayWord(param.args[0]);
                    if (word != param.args[0]) {
                        param.args[0] = word;
                        XposedBridge.log("FQHook+SearchWord: 搜索页推荐提示替换为通用提示");
                    }
                }
            });
            XposedBridge.log("FQHook+SearchWord: 已安装搜索页提示词显示清理: " + method);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+SearchWord: 搜索页提示词显示 Hook 失败: ", throwable);
        }
    }

    private void hookSearchCueList(String key, final String label) {
        try {
            Method method = cachedTargets.method(key);
            if (method == null) {
                XposedBridge.log("FQHook+SearchWord: 未找到" + label + "热词数据入口，跳过");
                return;
            }
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != List.class
                    || method.getParameterTypes().length != 1 || method.getParameterTypes()[0] != List.class) {
                XposedBridge.log("FQHook+SearchWord: " + label + "热词入口签名不兼容，跳过: " + method);
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args[0] instanceof List<?>) {
                        int count = searchCueWordFilter.clearList((List<?>) param.args[0]);
                        if (count > 0) {
                            XposedBridge.log("FQHook+SearchWord: 已清理" + label + "搜索框热词 " + count + " 项");
                        }
                    }
                }
            });
            XposedBridge.log("FQHook+SearchWord: 已安装" + label + "热词数据入口清理: " + method);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+SearchWord: " + label + "热词数据入口 Hook 失败: ", throwable);
        }
    }

    public void applySlidingTabHooks() {
        Method method = cachedTargets.method(HookTargets.KEY_TOP_TAP_METHOD);
        if (method == null) {
            return;
        }

        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object tabData = param.args[0];
                    if (tabData != null) {
                        processTabData(tabData);
                    }
                }
            });
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook-SlidingTab: Hook失败: ", throwable);
        }
    }

}
