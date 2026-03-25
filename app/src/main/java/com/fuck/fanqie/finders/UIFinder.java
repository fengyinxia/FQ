package com.fuck.fanqie.finders;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.TargetScanResult;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

public class UIFinder extends BaseFinder {
    public UIFinder(TargetScanResult scanResult) {
        super(scanResult);
    }

    @Override
    public void find(DexKitBridge bridge) {
        findRedDotMethod(bridge);
        findFeatureListLoadMethod(bridge);
        findVipRelatedTargets(bridge);
        findSearchBarMethod(bridge);
        findFilterHomeMethod(bridge);
        findTabMethod(bridge);
        findDynamicMethod(bridge);
        findBookNameClickMethod(bridge);
        findMyPageSearchBarMethod(bridge);
        findGameAreaMethod(bridge);
        findMsgAreaMethod(bridge);
    }

    private void findBookNameClickMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .usingStrings(new String[]{"ivBookName", "tvBookName"})
                                    .addInvoke("Landroid/widget/ImageView;->setOnClickListener(Landroid/view/View$OnClickListener;)V")
                    )
            ));
            cacheMethod(HookTargets.KEY_BOOK_NAME_CLICK_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找书名点击方法失败", throwable);
        }
    }

    private void findDynamicMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create().usingStrings(new String[]{"没命中动态卡复用实验"})
                    )
            ));
            cacheMethod(HookTargets.KEY_DYNAMIC_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找动态卡片方法失败", throwable);
        }
    }

    private void findFeatureListLoadMethod(DexKitBridge bridge) {
        try {
            ClassData classData = first(bridge.findClass(
                    FindClass.create().matcher(
                            ClassMatcher.create().usingStrings(new String[]{"CardData(cardInfoList="})
                    )
            ));
            cacheClass(HookTargets.KEY_FEATURE_LIST_LOAD_CLASS, classData);
        } catch (Throwable throwable) {
            log("查找功能列表加载类失败", throwable);
        }
    }

    private void findFilterHomeMethod(DexKitBridge bridge) {
        MethodData filterDataMethod = null;
        try {
            filterDataMethod = first(bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read"})
                            .matcher(
                                    MethodMatcher.create()
                                            .paramTypes(new String[]{"com.dragon.read.rpc.model.CellViewData", "int", "int"})
                                            .returnType("java.util.List")
                                            .modifiers(java.lang.reflect.Modifier.PUBLIC | java.lang.reflect.Modifier.STATIC)
                                            .addInvoke("Ljava/lang/Enum;->ordinal()I")
                            )
            ));
            cacheMethod(HookTargets.KEY_FILTER_DATA_METHOD, filterDataMethod);
        } catch (Throwable throwable) {
            log("查找筛选数据方法失败", throwable);
        }

        if (filterDataMethod == null) {
            return;
        }

        try {
            MethodData filterBannerMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass(filterDataMethod.getDeclaredClassName())
                                    .paramTypes(new String[]{"com.dragon.read.rpc.model.CellViewData", "int"})
                                    .returnType("com.dragon.read.feed.bookmall.card.model.staggered.BaseInfiniteModel")
                    )
            ));
            cacheMethod(HookTargets.KEY_FILTER_BANNER_METHOD, filterBannerMethod);

            if (filterBannerMethod != null) {
                MethodData removeRankMethod = first(bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .declaredClass(filterBannerMethod.getDeclaredClassName())
                                        .paramTypes(new String[]{"com.dragon.read.rpc.model.CellViewData", "int"})
                                        .addInvoke("Lcom/dragon/read/component/biz/impl/bookmall/holder/mainrank/RankMixContentHolder$RankMixContentModel;-><init>()V")
                        )
                ));
                cacheMethod(HookTargets.KEY_REMOVE_RANK_METHOD, removeRankMethod);
            }
        } catch (Throwable throwable) {
            log("查找首页过滤相关方法失败", throwable);
        }
    }

    private void findGameAreaMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read.kmp.mine.sidebar"})
                            .matcher(
                                    MethodMatcher.create()
                                            .usingStrings(new String[]{"com.dragon.read.kmp.mine.sidebar.GameArea"})
                                            .paramCount(3)
                            )
            ));
            cacheMethod(HookTargets.KEY_GAME_AREA_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找游戏区域方法失败", throwable);
        }
    }

    private void findMsgAreaMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read.kmp.mine.sidebar"})
                            .matcher(
                                    MethodMatcher.create()
                                            .usingStrings(new String[]{"com.dragon.read.kmp.mine.sidebar.MsgArea"})
                                            .paramCount(3)
                            )
            ));
            cacheMethod(HookTargets.KEY_MSG_AREA_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找消息区域方法失败", throwable);
        }
    }

    private void findMyPageSearchBarMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2")
                                    .addInvoke("Lcom/dragon/read/util/UiUtils;->setTopMargin(Landroid/view/View;F)V")
                                    .addInvoke("Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V")
                    )
            ));
            cacheMethod(HookTargets.KEY_MY_PAGE_SEARCH_BAR_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找我的页面搜索栏方法失败", throwable);
        }
    }

    private void findRedDotMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .usingStrings(new String[]{"red_point"})
                                    .addInvoke("Lcom/dragon/read/util/UiUtils;->setVisibility(Landroid/view/View;I)V")
                    )
            ));
            cacheMethod(HookTargets.KEY_RED_DOT_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找红点方法失败", throwable);
        }
    }

    private void findSearchBarMethod(DexKitBridge bridge) {
        try {
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read"})
                            .matcher(
                                    MethodMatcher.create().usingStrings(new String[]{"搜索中间页加载成功"})
                            )
            ));
            cacheMethod(HookTargets.KEY_SEARCH_BAR_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找搜索栏相关方法失败", throwable);
        }
    }

    private void findTabMethod(DexKitBridge bridge) {
        try {
            MethodData bottomTabMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("com.dragon.read.pages.main.MainFragmentActivity")
                                    .paramTypes(new String[]{"com.dragon.read.widget.BottomTabBarLayout", "boolean"})
                    )
            ));
            cacheMethod(HookTargets.KEY_TAB_METHOD, bottomTabMethod);
        } catch (Throwable throwable) {
            log("查找底部 tab 方法失败", throwable);
        }

        try {
            MethodData topTabMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("com.dragon.read.component.biz.impl.NewBookMallFragment")
                                    .usingStrings(new String[]{"更新首屏ui， 是否来自首屏缓存数据:%s"})
                    )
            ));
            cacheMethod(HookTargets.KEY_TOP_TAP_METHOD, topTabMethod);
        } catch (Throwable throwable) {
            log("查找顶部 tab 方法失败", throwable);
        }
    }

    private void findVipRelatedTargets(DexKitBridge bridge) {
        try {
            MethodData vipEntranceMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2")
                                    .addInvoke("Lcom/dragon/read/component/interfaces/NsAcctManager;->isOfficial()Z")
                    )
            ));
            cacheMethod(HookTargets.KEY_MY_PAGE_VIP_ENTRANCE_METHOD, vipEntranceMethod);
        } catch (Throwable throwable) {
            log("查找我的页面 VIP 入口失败", throwable);
        }

        try {
            ClassData vipInfoModelClass = first(bridge.findClass(
                    FindClass.create().matcher(
                            ClassMatcher.create().usingStrings(new String[]{"VipInfoModel{expireTime='"})
                    )
            ));
            cacheClass(HookTargets.KEY_VIP_INFO_MODEL_CLASS, vipInfoModelClass);
        } catch (Throwable throwable) {
            log("查找 VIP 信息模型类失败", throwable);
        }
    }
}
