package com.fuck.fanqie.hooks;

import android.view.View;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        applyCustomVipHooks();
        applySearchWordHooks();
        applySearchBarHooks();
        applyRecommendFlowHooks();
    }

    private void applyDisableMyPageSidebarHooks() {
        try {
            Class<?> configClass = XposedHelpers.findClass(
                    "com.dragon.read.base.ssconfig.template.GameRevisitPathV693Model",
                    hostClassLoader
            );
            XposedBridge.hookAllMethods(configClass, "b", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object config = param.getResult();
                    if (config != null) {
                        XposedHelpers.setBooleanField(config, "sideBarEnable", false);
                    }
                }
            });
            XposedBridge.log("FQHook+MyPageSidebar: 已禁用侧边栏配置");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageSidebar: Hook侧边栏配置失败: ", throwable);
        }
    }

    private boolean isWantedFeature(Object featureId) {
        if (featureId == null) {
            return false;
        }
        String value = featureId.toString();
        return "READING_HISTORY".equals(value)
                || "READING_PREFERENCE".equals(value)
                || "BOOK_DOWNLOAD".equals(value)
                || "MY_MESSAGE".equals(value);
    }

    @SuppressWarnings("unchecked")
    private void processTabData(Object target) {
        try {
            List<Object> tabDataList = (List<Object>) XposedHelpers.callMethod(target, "getBookMallTabDataList");
            if (tabDataList == null || tabDataList.isEmpty()) {
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
                    String tabName = (String) XposedHelpers.callMethod(tabData, "getTabName");
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
            XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
            XposedBridge.log("FQHook+MyPageSearch: 已禁用动态搜索入口构建");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageSearch: 禁用动态搜索入口失败: ", throwable);
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

    @SuppressWarnings("unchecked")
    public void applyRemoveMyPageExtraCardHooks() {
        final Class<?> featureListLoadClass = cachedTargets.type(HookTargets.KEY_FEATURE_LIST_LOAD_CLASS);
        if (featureListLoadClass == null) {
            XposedBridge.log("FQHook+RemoveCard: 未找到功能列表加载类，跳过Hook");
            return;
        }

        try {
            XposedHelpers.findAndHookConstructor(featureListLoadClass, List.class, new XC_MethodHook() {
                private final Map<Class<?>, Field> subItemsFieldCache = new HashMap<>();
                private final Map<Class<?>, Field> featureIdFieldCache = new HashMap<>();

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    List<Object> items = (List<Object>) param.args[0];
                    if (items == null || items.isEmpty()) {
                        return;
                    }

                    List<Object> filteredItems = new ArrayList<>();
                    for (Object item : items) {
                        if (item == null) {
                            continue;
                        }
                        try {
                            Field subItemsField = subItemsFieldCache.get(item.getClass());
                            if (subItemsField == null) {
                                subItemsField = XposedHelpers.findField(item.getClass(), "b");
                                subItemsFieldCache.put(item.getClass(), subItemsField);
                            }

                            List<Object> subItems = (List<Object>) subItemsField.get(item);
                            if (subItems == null) {
                                continue;
                            }

                            List<Object> keptSubItems = new ArrayList<>();
                            for (Object subItem : subItems) {
                                if (subItem == null) {
                                    continue;
                                }
                                Field featureIdField = featureIdFieldCache.get(subItem.getClass());
                                if (featureIdField == null) {
                                    featureIdField = XposedHelpers.findField(subItem.getClass(), "a");
                                    featureIdFieldCache.put(subItem.getClass(), featureIdField);
                                }

                                Object featureId = featureIdField.get(subItem);
                                if (isWantedFeature(featureId)) {
                                    keptSubItems.add(subItem);
                                } else {
                                    XposedBridge.log("FQHook+RemoveCard: 移除功能 - " + featureId);
                                }
                            }

                            if (!keptSubItems.isEmpty()) {
                                subItemsField.set(item, keptSubItems);
                                filteredItems.add(item);
                            }
                        } catch (Throwable throwable) {
                            XposedBridge.log("FQHook+RemoveCard: 处理项目异常: " + throwable.getMessage());
                            XposedBridge.log(throwable);
                            filteredItems.add(item);
                        }
                    }

                    param.args[0] = filteredItems;
                    XposedBridge.log("FQHook+RemoveCard: 已过滤我的页面卡片(剩余 " + filteredItems.size() + " 项)");
                }
            });
            XposedBridge.log("FQHook+RemoveCard: 成功Hook功能列表加载");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+RemoveCard: Hook失败: ", throwable);
        }
    }

    public void applyRemoveRankHooks() {
        Method method = cachedTargets.method(HookTargets.KEY_REMOVE_RANK_METHOD);
        if (method != null) {
            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(null));
        }
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
                    Object searchCueWord = param.args[0];
                    if (searchCueWord == null) {
                        return;
                    }
                    try {
                        Field textField = searchCueWord.getClass().getDeclaredField("text");
                        textField.setAccessible(true);
                        textField.set(searchCueWord, "");
                    } catch (Throwable throwable) {
                        XposedBridge.log("FQHook+applySearchWordHooks: text字段不存在: " + throwable.getMessage());
                    }
                }
            });
            XposedBridge.log("FQHook+applySearchWordHooks: 已成功hook SearchCueWordExtend构造方法");
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+applySearchWordHooks: hook SearchCueWordExtend构造方法失败: " + throwable.getMessage());
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
