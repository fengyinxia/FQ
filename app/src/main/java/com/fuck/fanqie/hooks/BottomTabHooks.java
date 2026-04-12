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
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class BottomTabHooks extends BaseHook {
    private static final String TAB_CONFIG_METHOD = "z";
    private static final List<String> PREFERRED_ORDER = Arrays.asList(
            "BookShelf",
            "BookStore",
            "MyProfile",
            "BookCategory",
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

    public BottomTabHooks(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        super(hostClassLoader);
        this.cachedTargets = cachedTargets;
    }

    @Override
    public void apply() {
        applyTabOrderHook();
        applyHiddenTabHook();
    }

    private void applyTabOrderHook() {
        try {
            Class<?> tabConfigClass = cachedTargets.type(HookTargets.KEY_TAB_ROUTE_HELPER_CLASS);
            if (tabConfigClass == null) {
                XposedBridge.log("FQHook+BottomTab: 未找到底栏路由实验帮助类，跳过前置排序");
                return;
            }
            XposedBridge.hookAllMethods(tabConfigClass, TAB_CONFIG_METHOD, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length != 0 || !(param.getResult() instanceof List)) {
                        return;
                    }
                    List<Object> orderedTabs = reorderTabs((List<?>) param.getResult());
                    if (orderedTabs == null) {
                        return;
                    }
                    param.setResult(orderedTabs);
                }
            });
            XposedBridge.log("FQHook+BottomTab: 已改为前置调整底栏数据源顺序");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+BottomTab: Hook 底栏数据源顺序失败: ", throwable);
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
        List<Object> sortedTabs = new ArrayList<Object>(tabs.size());
        for (Object tab : tabs) {
            if (tab != null) {
                sortedTabs.add(tab);
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
            Object type = XposedHelpers.callMethod(button, "f");
            if (type instanceof Enum<?>) {
                return ((Enum<?>) type).name();
            }
            if (type != null) {
                return String.valueOf(type);
            }
        } catch (Throwable ignored) {
        }
        return String.valueOf(button);
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
