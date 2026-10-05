package com.fuck.fanqie.finders;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.TargetScanResult;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.UsingType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class UIFinder extends BaseFinder {
    public UIFinder(TargetScanResult scanResult) {
        super(scanResult);
    }

    @Override
    public void find(DexKitBridge bridge) {
        findBookshelfBannerResponseMethod(bridge);
        findRedDotMethod(bridge);
        findQuickAccessTargets(bridge);
        findVipRelatedTargets(bridge);
        findSearchBarMethod(bridge);
        findFilterHomeMethod(bridge);
        findTabMethod(bridge);
        findTabRouteHelperClass(bridge);
        findDynamicMethod(bridge);
        findBookNameClickMethod(bridge);
        findMyPageSearchBarMethod(bridge);
        findMyPageRecommendEnableMethod(bridge);
        findMyPageContentMethod(bridge);
        findMyPageSettingsClickMethod(bridge);
        findCategoryTabTargets(bridge);
        findSearchCueListMethod(bridge, HookTargets.KEY_SEARCH_CUE_LIST_METHOD,
                "com.dragon.read.component.biz.impl.bookmall.search.SearchWordDisplayView");
        findSearchCueListMethod(bridge, HookTargets.KEY_SEARCH_CUE_KMP_LIST_METHOD,
                "com.dragon.read.kmp.bookmall.search.SearchWordDisplayViewKMP");
    }

    private void findCategoryTabTargets(DexKitBridge bridge) {
        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create()
                            .paramCount(0)
                            .returnType(Boolean.TYPE)
                            .addInvoke("Landroid/content/res/Resources;->getBoolean(I)Z")
                            .declaredClass(ClassMatcher.create().addMethod(
                                    MethodMatcher.create().paramCount(0).returnType(
                                            "com.dragon.read.component.biz.impl.category.optimized.kmp.KmpCategoryFragment")
                            ))
            ));
            if (methods.size() == 1 && methods.get(0).isMethod()) {
                cacheMethod(HookTargets.KEY_CATEGORY_TAB_DISABLED_METHOD, methods.get(0));
            } else {
                log("分类 Tab 门禁无法唯一定位，候选数=" + methods.size());
            }
        } catch (Throwable throwable) {
            log("查找分类 Tab 门禁失败", throwable);
        }
        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create()
                            .declaredClass("com.dragon.read.pages.main.MainFragmentActivity")
                            .name("onCreate")
                            .paramTypes(new String[]{"android.os.Bundle"})
                            .returnType(Void.TYPE)
            ));
            if (methods.size() == 1 && methods.get(0).isMethod()) {
                cacheMethod(HookTargets.KEY_MAIN_ACTIVITY_ON_CREATE_METHOD, methods.get(0));
            } else {
                log("主界面 onCreate 无法唯一定位，候选数=" + methods.size());
            }
        } catch (Throwable throwable) {
            log("查找主界面初始化方法失败", throwable);
        }
    }

    private void findSearchCueListMethod(DexKitBridge bridge, String key, String className) {
        try {
            // 两种搜索框各有唯一的静态 List -> List 预处理入口，不写死混淆方法名。
            List<MethodData> methods = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass(className)
                                    .modifiers(Modifier.STATIC)
                                    .paramTypes(new String[]{"java.util.List"})
                                    .returnType("java.util.List")
                    )
            );
            if (methods.size() != 1 || !methods.get(0).isMethod()) {
                log("搜索框热词列表入口无法唯一定位 " + key + "，候选数=" + methods.size());
                return;
            }
            cacheMethod(key, methods.get(0));
        } catch (Throwable throwable) {
            log("查找搜索框热词列表入口失败 " + key, throwable);
        }
    }

    private void findMyPageSettingsClickMethod(DexKitBridge bridge) {
        try {
            List<MethodData> methods = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .name("onClick")
                                    .paramTypes(new String[]{"android.view.View"})
                                    .returnType(Void.TYPE)
                                    .addUsingString("设置")
                                    .addInvoke("Lcom/dragon/read/component/interfaces/NsAppNavigator;->openSetting(Landroid/content/Context;Lcom/dragon/read/report/PageRecorder;)V")
                                    .declaredClass(ClassMatcher.create().addMethod(
                                            MethodMatcher.create()
                                                    .name("<init>")
                                                    .paramTypes(new String[]{"com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2"})
                                    ))
                    )
            );
            if (methods.size() != 1 || !methods.get(0).isMethod()) {
                log("我的页设置点击入口无法唯一定位，候选数=" + methods.size());
                for (MethodData candidate : methods) {
                    log("设置点击候选: " + candidate);
                }
                return;
            }
            cacheMethod(HookTargets.KEY_MY_PAGE_SETTINGS_CLICK_METHOD, methods.get(0));
        } catch (Throwable throwable) {
            log("查找我的页设置点击入口失败", throwable);
        }
    }

    private void findMyPageContentMethod(DexKitBridge bridge) {
        try {
            List<MethodData> methods = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2")
                                    .name("onCreateContent")
                                    .paramTypes(new String[]{"android.view.LayoutInflater",
                                            "android.view.ViewGroup", "android.os.Bundle"})
                                    .returnType("android.view.View")
                    )
            );
            if (methods.size() != 1 || !methods.get(0).isMethod()) {
                log("我的页内容创建入口无法唯一定位，候选数=" + methods.size());
                return;
            }
            cacheMethod(HookTargets.KEY_MY_PAGE_CONTENT_METHOD, methods.get(0));
        } catch (Throwable throwable) {
            log("查找我的页内容创建入口失败", throwable);
        }
    }

    private void findMyPageRecommendEnableMethod(DexKitBridge bridge) {
        try {
            // 仅关闭我的页瀑布流，不改全局推荐设置或整页创建方法。
            List<MethodData> methods = bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read.base.ssconfig.template"})
                            .matcher(
                                    MethodMatcher.create()
                                            .addUsingString("mine_tab_staggered_feed_v649")
                                            // 配置伴生类 a() 也满足字符串及签名，必须区分实际展示门禁。
                                            .addInvoke("Lcom/dragon/read/absettings/CommonAbResult;->getBookMallRevert()Z")
                                            .addInvoke("Lcom/dragon/read/app/AppRunningMode;->isFullMode()Z")
                                            .modifiers(Modifier.STATIC)
                                            .paramCount(0)
                                            .returnType(Boolean.TYPE)
                            )
            );
            if (methods.size() != 1 || !methods.get(0).isMethod()) {
                log("我的页推荐流开关无法唯一定位，候选数=" + methods.size());
                for (MethodData candidate : methods) {
                    log("推荐流开关候选: " + candidate);
                }
                return;
            }
            cacheMethod(HookTargets.KEY_MY_PAGE_RECOMMEND_ENABLE_METHOD, methods.get(0));
        } catch (Throwable throwable) {
            log("查找我的页推荐流开关失败", throwable);
        }
    }

    private void findBookshelfBannerResponseMethod(DexKitBridge bridge) {
        try {
            // 请求成功日志已挪到 Runnable.run()；7.3.9.32 的实际消费方是 g34.x.invoke(Object)。
            MethodData methodData = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("g34.x")
                                    .name("invoke")
                                    .paramTypes(new String[]{"java.lang.Object"})
                                    .returnType("java.lang.Object")
                    )
            ));
            if (methodData == null) {
                methodData = first(bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .usingStrings(new String[]{"request banner data success size:"})
                                        .paramTypes(new String[]{"com.dragon.read.rpc.model.GetBookShelfBannerResponse"})
                        )
                ));
            }
            cacheMethod(HookTargets.KEY_BOOKSHELF_BANNER_RESPONSE_METHOD, methodData);
        } catch (Throwable throwable) {
            log("查找书架 Banner 响应方法失败", throwable);
        }
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

    private void findQuickAccessTargets(DexKitBridge bridge) {
        try {
            List<MethodData> candidates = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(ClassMatcher.create().usingStrings(
                                    new String[]{"FunctionItemConverter"}))
                            .paramCount(4)
                            .returnType(List.class)));
            List<MethodData> converters = new ArrayList<>();
            for (MethodData method : candidates) {
                List<String> params = method.getParamTypeNames();
                if (method.isMethod()
                        && "androidx.fragment.app.FragmentActivity".equals(params.get(1))
                        && "kotlin.jvm.functions.Function1".equals(params.get(3))) {
                    converters.add(method);
                }
            }
            if (converters.size() != 1) {
                log("快捷功能转换无法唯一定位，候选数=" + converters.size());
                return;
            }
            MethodData converter = converters.get(0);
            cacheMethod(HookTargets.KEY_QUICK_ACCESS_CONVERT_METHOD, converter);
            // 保留原类 key 的含义；Hook 层改读新方法 key，不再按类名分流。
            cacheClass(HookTargets.KEY_FEATURE_LIST_LOAD_CLASS, converter.getDeclaredClass());

            List<MethodData> aggregates = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.component.biz.impl.mine"})
                    .matcher(MethodMatcher.create()
                            .paramTypes(converter.getParamTypeNames().subList(0, 3))
                            .returnType(List.class)
                            .addInvoke(converter.getDescriptor())
                            .addUsingField("Lcom/dragon/read/component/biz/api/model/CardType;->COMMON:Lcom/dragon/read/component/biz/api/model/CardType;", UsingType.Read)
                            .addInvoke("Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z")));
            if (aggregates.size() == 1 && aggregates.get(0).isMethod()) {
                cacheMethod(HookTargets.KEY_QUICK_ACCESS_AGGREGATE_METHOD, aggregates.get(0));
            } else {
                log("快捷功能聚合无法唯一定位，候选数=" + aggregates.size());
            }
        } catch (Throwable throwable) {
            log("查找新版快捷功能目标失败", throwable);
        }
    }

    private void findFilterHomeMethod(DexKitBridge bridge) {
        try {
            MethodData filterDataMethod = first(bridge.findMethod(
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

    private void findTabRouteHelperClass(DexKitBridge bridge) {
        try {
            ClassData classData = first(bridge.findClass(
                    FindClass.create()
                            .searchPackages(new String[]{"com.dragon.read.pages.main"})
                            .matcher(ClassMatcher.create().usingStrings(new String[]{"TabRouteExperimentHelper"}))
            ));
            if (classData == null) {
                classData = first(bridge.findClass(
                        FindClass.create().matcher(
                                ClassMatcher.create().usingStrings(new String[]{"TabRouteExperimentHelper"})
                        )
                ));
            }
            cacheClass(HookTargets.KEY_TAB_ROUTE_HELPER_CLASS, classData);
        } catch (Throwable throwable) {
            log("查找底栏路由实验帮助类失败", throwable);
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
