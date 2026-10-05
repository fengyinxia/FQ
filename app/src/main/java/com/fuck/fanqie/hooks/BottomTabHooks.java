package com.fuck.fanqie.hooks;

import android.view.View;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class BottomTabHooks extends BaseHook {
    private static final List<String> PREFERRED_ORDER = Arrays.asList(
            "BookShelf",
            "BookStore",
            "BookCategory",
            "MyProfile",
            "LuckyBenefit",
            "VideoSeriesFeedTab",
            "Novel",
            "Community",
            "ShopMall"
    );
    private static final Set<String> HIDDEN_TAB_TYPES = new HashSet<String>(Collections.singletonList(
            "VideoSeriesFeedTab"
    ));
    private static final Map<String, Integer> PRIORITY = createPriority();

    private final CachedTargets cachedTargets;
    private boolean restoreCategoryTab;
    private boolean refreshHooksInstalled;

    public BottomTabHooks(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        super(hostClassLoader);
        this.cachedTargets = cachedTargets;
    }

    @Override
    public void apply() {
        applyCategoryTabGateHook();
        applyTabOrderHook();
        applyHiddenTabHook();
    }

    private void applyCategoryTabGateHook() {
        try {
            Method method = cachedTargets.method(HookTargets.KEY_CATEGORY_TAB_DISABLED_METHOD);
            if (method == null || method.getReturnType() != boolean.class
                    || method.getParameterTypes().length != 0) {
                XposedBridge.log("FQHook+BottomTab: 分类资源门禁缺失/不兼容，保留原分类入口");
                return;
            }
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(Boolean.FALSE));
            restoreCategoryTab = true;
            XposedBridge.log("FQHook+BottomTab: 已安装原生分类 Tab 资源门禁拦截");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+BottomTab: 分类 Tab 门禁 Hook 失败: ", throwable);
        }
    }

    private void applyTabOrderHook() {
        try {
            final Class<?> tabConfigClass = cachedTargets.type(HookTargets.KEY_TAB_ROUTE_HELPER_CLASS);
            Method buildTabs = cachedTargets.method(HookTargets.KEY_TAB_METHOD);
            if (tabConfigClass == null || buildTabs == null) {
                XposedBridge.log("FQHook+BottomTab: 缺少路由帮助类或底栏构建方法，跳过排序");
                return;
            }
            Method onCreate = cachedTargets.method(HookTargets.KEY_MAIN_ACTIVITY_ON_CREATE_METHOD);
            if (restoreCategoryTab && onCreate != null) {
                XposedBridge.hookMethod(onCreate, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // 此时 Application 已初始化；不要在 attachBaseContext 时强行初始化路由类。
                        installTabSourceRefreshHooks(tabConfigClass);
                        normalizeTabSource(tabConfigClass);
                    }
                });
            } else if (restoreCategoryTab) {
                XposedBridge.log("FQHook+BottomTab: 主界面初始化目标缺失，仅在底栏构建前处理");
            }
            // 底栏生成前再次归一化，宿主自身重建按钮、布局约束和导航索引。
            XposedBridge.hookMethod(buildTabs, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (restoreCategoryTab) {
                        installTabSourceRefreshHooks(tabConfigClass);
                    }
                    normalizeTabSource(tabConfigClass);
                }
            });
            XposedBridge.log("FQHook+BottomTab: 已在构建底栏前接入数据源排序");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+BottomTab: Hook 底栏数据源顺序失败: ", throwable);
        }
    }

    private void installTabSourceRefreshHooks(final Class<?> tabConfigClass) {
        if (refreshHooksInstalled) {
            return;
        }
        refreshHooksInstalled = true;
        int count = 0;
        for (Method method : tabConfigClass.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getReturnType() != void.class || types.length != 1
                    || !"com.dragon.read.rpc.model.BookStoreAlignmentData".equals(types[0].getName())) {
                continue;
            }
            try {
                // tabBarList 和 tabBarTypes 都可能重建 e，跟随宿主更新结果而非盲改响应元数据。
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!param.hasThrowable()) {
                            normalizeTabSource(tabConfigClass);
                        }
                    }
                });
                count++;
            } catch (Throwable throwable) {
                HookUtils.logError("FQHook+BottomTab: 底栏配置刷新 Hook 失败: " + method + ": ", throwable);
            }
        }
        XposedBridge.log("FQHook+BottomTab: 已安装 " + count + " 个底栏配置刷新入口归一化");
    }

    private void normalizeTabSource(Class<?> tabConfigClass) {
        try {
            Object value = XposedHelpers.getStaticObjectField(tabConfigClass, "e");
            if (!(value instanceof List)) {
                XposedBridge.log("FQHook+BottomTab: 路由类型列表不存在，跳过");
                return;
            }
            List<Object> orderedTabs = reorderTabs((List<?>) value);
            if (orderedTabs != null) {
                XposedHelpers.setStaticObjectField(tabConfigClass, "e", new ArrayList<>(orderedTabs));
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+BottomTab: 归一化底栏类型列表失败: ", throwable);
        }
    }

    private void applyHiddenTabHook() {
        Method method = cachedTargets.method(HookTargets.KEY_TAB_METHOD);
        if (method == null) {
            return;
        }

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object bottomTabLayout = param.args.length > 0 ? param.args[0] : null;
                if (bottomTabLayout == null) {
                    bottomTabLayout = XposedHelpers.getObjectField(param.thisObject, "k");
                }
                if (bottomTabLayout == null) {
                    return;
                }
                hideTabs(bottomTabLayout);
            }
        });
        XposedBridge.log("FQHook+BottomTab: 已启用视频 Tab 隐藏");
    }

    private void hideTabs(Object bottomTabLayout) {
        try {
            List<Object> tabButtons = getTabButtons(bottomTabLayout);
            if (tabButtons == null || tabButtons.isEmpty()) {
                return;
            }
            applyHiddenTabs(tabButtons);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+BottomTab: 隐藏视频 Tab 失败: ", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getTabButtons(Object bottomTabLayout) {
        Object value = XposedHelpers.callMethod(bottomTabLayout, "getTabButtonList");
        return value instanceof List ? (List<Object>) value : null;
    }

    private List<Object> reorderTabs(List<?> tabs) {
        if (tabs == null || tabs.isEmpty()) {
            return null;
        }
        List<Object> sortedTabs = new ArrayList<Object>(tabs.size() + 1);
        boolean hasCategory = false;
        Enum<?> storeType = null;
        for (Object tab : tabs) {
            String name = resolveTypeName(tab);
            if (tab == null || HIDDEN_TAB_TYPES.contains(name)) {
                continue;
            }
            if ("BookStore".equals(name) && tab instanceof Enum<?>) {
                storeType = (Enum<?>) tab;
            }
            if ("BookCategory".equals(name)) {
                if (hasCategory) {
                    continue;
                }
                hasCategory = true;
            }
            sortedTabs.add(tab);
        }
        if (restoreCategoryTab && !hasCategory && storeType != null) {
            for (Object type : storeType.getDeclaringClass().getEnumConstants()) {
                if ("BookCategory".equals(((Enum<?>) type).name())) {
                    sortedTabs.add(type);
                    hasCategory = true;
                    break;
                }
            }
            if (!hasCategory) {
                XposedBridge.log("FQHook+BottomTab: 宿主枚举中没有分类 Tab，保留顶部入口");
            }
        }
        Collections.sort(sortedTabs, new Comparator<Object>() {
            @Override
            public int compare(Object left, Object right) {
                return priorityOf(left) - priorityOf(right);
            }
        });
        if (sameOrder(tabs, sortedTabs)) {
            return null;
        }
        XposedBridge.log("FQHook+BottomTab: 已调整底栏顺序 -> " + describeOrder(sortedTabs));
        return sortedTabs;
    }

    private void applyHiddenTabs(List<Object> tabButtons) {
        for (Object button : tabButtons) {
            if (!HIDDEN_TAB_TYPES.contains(resolveTypeName(button))) {
                continue;
            }
            View view = resolveView(button);
            if (view == null) {
                continue;
            }
            view.setVisibility(View.GONE);
            view.setEnabled(false);
            view.setClickable(false);
            XposedBridge.log("FQHook+BottomTab: 已隐藏未从数据源移除的视频 Tab");
        }
    }

    private View resolveView(Object button) {
        Object value = XposedHelpers.callMethod(button, "getView");
        return value instanceof View ? (View) value : null;
    }

    private int priorityOf(Object button) {
        String typeName = resolveTypeName(button);
        Integer priority = PRIORITY.get(typeName);
        return priority == null ? Integer.MAX_VALUE : priority.intValue();
    }

    private String resolveTypeName(Object button) {
        if (button instanceof Enum<?>) {
            return ((Enum<?>) button).name();
        }
        if (button == null) {
            return null;
        }
        try {
            Object type = XposedHelpers.callMethod(button, "a");
            if (type instanceof Enum<?>) {
                return ((Enum<?>) type).name();
            }
        } catch (Throwable ignored) {
            // 旧版按钮仍可能通过 f() 返回枚举。
        }
        try {
            Object oldType = XposedHelpers.callMethod(button, "f");
            if (oldType instanceof Enum<?>) {
                return ((Enum<?>) oldType).name();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean sameOrder(List<?> left, List<?> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) {
                return false;
            }
        }
        return true;
    }

    private String describeOrder(List<Object> tabButtons) {
        List<String> names = new ArrayList<String>(tabButtons.size());
        for (Object button : tabButtons) {
            String typeName = resolveTypeName(button);
            if (typeName != null) {
                names.add(typeName);
            }
        }
        return names.toString();
    }

    private static Map<String, Integer> createPriority() {
        Map<String, Integer> priority = new HashMap<String, Integer>();
        for (int i = 0; i < PREFERRED_ORDER.size(); i++) {
            priority.put(PREFERRED_ORDER.get(i), Integer.valueOf(i));
        }
        return priority;
    }
}
