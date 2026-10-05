package com.fuck.fanqie.hooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class UIHooks extends BaseHook {
    private static final Set<String> ALLOWED_RECOMMEND_GROUP_TYPES = new HashSet<>(Arrays.asList(
            "Book",
            "RankListBook"
    ));
    private final CachedTargets cachedTargets;
    private final RecommendFilterHelper recommendFilterHelper;
    private final SearchCueWordFilter searchCueWordFilter = new SearchCueWordFilter();

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
        applyCustomVipHooks();
        applySearchWordHooks();
        applySearchBarHooks();
        applyRecommendFlowHooks();
    }

    private void applyMyPageWalletHooks() {
        try {
            Method method = cachedTargets.method(HookTargets.KEY_MY_PAGE_CONTENT_METHOD);
            if (method == null || !isPageContentFactory(method)) {
                XposedBridge.log("FQHook+MyPageWallet: 未找到兼容的我的页内容创建入口，跳过");
                return;
            }
            final Method settingsClick = cachedTargets.method(HookTargets.KEY_MY_PAGE_SETTINGS_CLICK_METHOD);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.getResult() instanceof View)) {
                        return;
                    }
                    final View root = (View) param.getResult();
                    // 7.3.9.32：feq 包含钱包行 he1 和福利领取 gos，整卡隐藏才不会残留福利。
                    final int[] ids = resolveMyPageIds(root, "feq", "he1", "frc", "fr5", "gou", "gos");
                    // 复用菜单按钮的位置，绑定原生设置回调；不能显示与夜间按钮重叠的备用 f05。
                    final int[] headerIds = resolveMyPageIds(root, "f0l", "f05");
                    final View.OnClickListener settingsListener = createMyPageSettingsListener(settingsClick, param.thisObject);
                    final int settingsIcon = root.getResources().getIdentifier(
                            "skin_mine_icon_setting_new_light", "drawable", "com.dragon.read");
                    if (settingsIcon == 0) {
                        XposedBridge.log("FQHook+MyPageHeader: 原生设置图标不存在，保留原菜单入口");
                    }
                    if (!hideMyPageWallet(root, ids)) {
                        XposedBridge.log("FQHook+MyPageWallet: 钱包福利卡尚未出现或结构不匹配，等待后续布局");
                    }
                    if (replaceMyPageMenuWithSettings(root, headerIds, settingsListener, settingsIcon)) {
                        XposedBridge.log("FQHook+MyPageHeader: 已在原侧栏菜单位置替换设置图标与点击行为");
                    }
                    root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            hideMyPageWallet(root, ids);
                            replaceMyPageMenuWithSettings(root, headerIds, settingsListener, settingsIcon);
                        }
                    });
                }
            });
            XposedBridge.log("FQHook+MyPageWallet: 已安装钱包福利卡隐藏及菜单替换设置入口 Hook");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageWallet: 隐藏我的页指定区域失败: ", throwable);
        }
    }

    private static int[] resolveMyPageIds(View root, String... names) {
        int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = root.getResources().getIdentifier(names[i], "id", "com.dragon.read");
            if (ids[i] == 0) {
                XposedBridge.log("FQHook+MyPageWallet: 资源 id/" + names[i] + " 不存在，跳过对应区域");
            }
        }
        return ids;
    }

    private static boolean hideMyPageWallet(View root, int[] ids) {
        for (int id : ids) {
            if (id == 0) {
                return false;
            }
        }
        View wallet = root.findViewById(ids[0]);
        if (!(wallet instanceof ViewGroup)) {
            return false;
        }
        View row = wallet.findViewById(ids[1]);
        if (!(row instanceof ViewGroup) || !(wallet.findViewById(ids[5]) instanceof ViewGroup)) {
            return false;
        }
        for (int i = 2; i < 5; i++) {
            if (row.findViewById(ids[i]) == null) {
                return false;
            }
        }
        if (wallet.getVisibility() != View.GONE) {
            wallet.setVisibility(View.GONE);
            XposedBridge.log("FQHook+MyPageWallet: 已隐藏金币余额提现及福利领取整卡 (id/feq)");
        }
        return true;
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

    private static boolean replaceMyPageMenuWithSettings(View root, int[] ids,
            View.OnClickListener listener, int iconId) {
        if (listener == null || iconId == 0 || ids[0] == 0 || ids[1] == 0) {
            return false;
        }
        View menu = root.findViewById(ids[0]);
        View settings = root.findViewById(ids[1]);
        if (!(menu instanceof android.widget.ImageView) || !(settings instanceof android.widget.ImageView)) {
            return false;
        }
        // 保留 f0l 原有布局参数和坐标；只替换图标及回调，f05 仍保留原生隐藏占位。
        menu.setOnClickListener(listener);
        ((android.widget.ImageView) menu).setImageResource(iconId);
        settings.setVisibility(View.INVISIBLE);
        menu.setVisibility(View.VISIBLE);
        return true;
    }

    private void applyDisableMyPageRecommendHooks() {
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

    private boolean isWantedFeature(Object featureId) {
        if (featureId == null) {
            return false;
        }
        String value = featureId.toString();
        String id = value.toUpperCase(Locale.ROOT);
        return "READING_HISTORY".equals(id)
                || "READING_PREFERENCE".equals(id)
                || "BOOK_DOWNLOAD".equals(id)
                || "MY_MESSAGE".equals(id)
                || "浏览历史".equals(value)
                || "阅读偏好".equals(value)
                || "下载管理".equals(value)
                || "我的消息".equals(value);
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

            Set<String> allowedTabs = new HashSet<>(Arrays.asList(
                    "推荐",
                    "小说",
                    "经典",
                    "知识",
                    "新书",
                    "视频"
            ));
            List<Object> filtered = new ArrayList<>();
            for (Object tabData : tabDataList) {
                try {
                    String tabName = (String) XposedHelpers.getObjectField(tabData, "tabName");
                    if (tabName != null && allowedTabs.contains(tabName)) {
                        filtered.add(tabData);
                    }
                } catch (Throwable throwable) {
                    XposedBridge.log("FQHook-SlidingTab: 处理标签失败: " + throwable);
                }
            }

            if (filtered.isEmpty() && !tabDataList.isEmpty()) {
                filtered.add(tabDataList.get(0));
            }

            XposedHelpers.setObjectField(target, "bookMallTabDataList", filtered);
            XposedHelpers.setIntField(target, "selectIndex", 0);
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook-SlidingTab: 过滤逻辑失败: " + throwable);
        }
    }

    public void applyCustomVipHooks() {
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
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(null));
            XposedBridge.log("FQHook+applyHooks: 已禁用我的页面VIP入口");
        }
    }

    public void applyMyPageSearchHooks() {
        disableMyPageDynamicSearch();
    }

    private void disableMyPageDynamicSearch() {
        Method method = cachedTargets.method(HookTargets.KEY_MY_PAGE_SEARCH_BAR_METHOD);
        if (method == null) {
            return;
        }
        try {
            if (isPageContentFactory(method)) {
                // 7.3.9.32 的搜索入口是我的页头部 id/g64 的 ComposeView，
                // 不能替换整页 onCreateContent：保留原结果，只隐藏这个具体控件。
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (!(result instanceof View)) {
                            return;
                        }
                        final View root = (View) result;
                        final int searchId = root.getResources().getIdentifier("g64", "id", "com.dragon.read");
                        if (searchId == 0) {
                            XposedBridge.log("FQHook+MyPageSearch: 我的页搜索控件 id/g64 不存在，跳过隐藏");
                            return;
                        }
                        hideMyPageSearch(root, searchId);
                        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                hideMyPageSearch(root, searchId);
                            }
                        });
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

    /** 隐藏搜索入口但保留占位，避免头部扫描和夜间模式按钮重新排版。 */
    private static void hideMyPageSearch(View root, int searchId) {
        View search = root.findViewById(searchId);
        if (search != null && search.getClass().getName().contains("ComposeView")
                && search.getVisibility() != View.INVISIBLE) {
            search.setVisibility(View.INVISIBLE);
            XposedBridge.log("FQHook+MyPageSearch: 已隐藏我的页搜索入口并保留占位 (id/g64)");
        }
    }

    public void applyRedDotHooks() {
        Method method = cachedTargets.method(HookTargets.KEY_RED_DOT_METHOD);
        if (method == null) {
            return;
        }
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0) {
                    param.args[0] = Boolean.FALSE;
                    XposedBridge.log("FQHook+RedDot: 已禁用红点显示");
                }
            }
        });
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
                        if (!(cardType instanceof Enum)
                                || !"QUICK_ACCESS".equals(((Enum<?>) cardType).name())) {
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
                                    || isWantedFeature(((Enum<?>) featureType).name())) {
                                kept.add(item);
                            }
                        }
                        XposedBridge.log("FQHook+RemoveCard: 快捷功能候选=" + items.size()
                                + "，允许=" + kept.size());
                        if (!kept.isEmpty() && kept.size() != items.size()) {
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
                        String type = item.getClass().getName();
                        if ("de4.v0".equals(type) || "de4.x0".equals(type)
                                || "de4.i".equals(type) || "de4.p0".equals(type)) {
                            kept.add(item);
                        }
                    }
                    if (kept.isEmpty()) {
                        XposedBridge.log("FQHook+RemoveCard: 聚合列表未识别允许项，保留原列表");
                    } else if (kept.size() != items.size()) {
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
        Method method = cachedTargets.method(HookTargets.KEY_FILTER_DATA_METHOD);
        if (method == null) {
            XposedBridge.log("FQHook+RecommendFlow: 未找到推荐流过滤方法");
            return;
        }

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object cellViewData = param.args.length > 0 ? param.args[0] : null;
                String reason = recommendFilterHelper.getFilterReason(cellViewData, ALLOWED_RECOMMEND_GROUP_TYPES);
                if (reason == null) {
                    return;
                }
                param.setResult(new ArrayList<>());
                XposedBridge.log("FQHook+RecommendFlow: 已过滤推荐流卡片 " + reason);
            }
        });
        XposedBridge.log("FQHook+RecommendFlow: 保留 Book / RankListBook，并额外过滤听书样式卡片");
    }

    @SuppressWarnings("unchecked")
    public void applySearchBarHooks() {
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
        // 运行时接收列表再清理，覆盖 JSON、缓存及 KMP 转换，不依赖构造器命中。
        hookSearchCueList(HookTargets.KEY_SEARCH_CUE_LIST_METHOD, "原生");
        hookSearchCueList(HookTargets.KEY_SEARCH_CUE_KMP_LIST_METHOD, "KMP");
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
