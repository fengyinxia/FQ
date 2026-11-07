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
        findMineRedDotTargets(bridge);
        findQuickAccessTargets(bridge);
        findVipRelatedTargets(bridge);
        findSearchBarMethod(bridge);
        findSearchCueDisplayMethod(bridge);
        findSearchResultMethods(bridge);
        findFilterHomeMethod(bridge);
        findRecommendParseMethods(bridge);
        findTabMethod(bridge);
        findTabRouteHelperClass(bridge);
        findDynamicMethod(bridge);
        findMyPageSearchBarMethod(bridge);
        findMyPageRecommendEnableMethod(bridge);
        findMyPageContentMethod(bridge);
        findMyPageSettingsClickMethod(bridge);
        findMyPageViewTargets(bridge);
        findKmpMyPageTargets(bridge);
        findCategoryTabTargets(bridge);
        findSettingsEntryTargets(bridge);
        findSearchCueListMethod(bridge, HookTargets.KEY_SEARCH_CUE_LIST_METHOD,
                "com.dragon.read.component.biz.impl.bookmall.search.SearchWordDisplayView");
        findSearchCueListMethod(bridge, HookTargets.KEY_SEARCH_CUE_KMP_LIST_METHOD,
                "com.dragon.read.kmp.bookmall.search.SearchWordDisplayViewKMP");
    }

    private void findSearchCueDisplayMethod(DexKitBridge bridge) {
        try {
            cacheUniqueMethod(HookTargets.KEY_SEARCH_CUE_DISPLAY_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass("com.dragon.read.component.biz.impl.FanqieSearchActivity")
                            .paramTypes("com.dragon.read.rpc.model.SearchCueWord").returnType(Void.TYPE)
                            .addUsingField("Lcom/dragon/read/rpc/model/SearchCueWord;->cueFormatStr:Ljava/lang/String;", UsingType.Read)
                            .addInvoke("Lcom/dragon/read/component/biz/impl/ui/speech/SearchEditTextView;->setHint(Ljava/lang/String;)V"))));
        } catch (Throwable error) {
            log("查找搜索页提示词显示入口失败", error);
        }
    }

    private void findSearchResultMethods(DexKitBridge bridge) {
        try {
            cacheUniqueMethod(HookTargets.KEY_SEARCH_RESPONSE_METHOD, bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.component.biz.impl.help"})
                    .matcher(MethodMatcher.create().modifiers(Modifier.STATIC).returnType(Void.TYPE)
                            .paramTypes("com.dragon.read.rpc.model.GetSearchTabDataResponse")
                            .addUsingField("Lcom/dragon/read/rpc/model/BookApiERR;->SUCCESS:Lcom/dragon/read/rpc/model/BookApiERR;", UsingType.Read)
                            .addInvoke("Lcom/dragon/read/rpc/model/BookApiERR;->getValue()I"))));
            cacheUniqueMethod(HookTargets.KEY_SEARCH_TABS_METHOD, bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.component.biz.impl.ui"})
                    .matcher(MethodMatcher.create().returnType(Void.TYPE).paramCount(6)
                            .addUsingString("init_tab_data_start")
                            .addUsingField("Lcom/dragon/read/rpc/model/GetSearchTabDataResponse;->searchTabs:Ljava/util/List;", UsingType.Read))));
            cacheUniqueMethod(HookTargets.KEY_SEARCH_AUTO_FILL_METHOD, bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.component.biz.impl.ui.searchpage.fragment"})
                    .matcher(MethodMatcher.create().name("run").paramCount(0).returnType(Void.TYPE)
                            .addUsingString("firstPageNotFull"))));
            cacheUniqueMethod(HookTargets.KEY_SEARCH_PARSE_METHOD, bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.component.biz.impl.help"})
                    .matcher(MethodMatcher.create().paramTypes("java.util.List").returnType(List.class)
                            .addUsingString("有个页卡没有show_type且不是lynx卡片，忽略"))));
        } catch (Throwable error) {
            log("查找搜索结果过滤入口失败", error);
        }
    }

    private void findSettingsEntryTargets(DexKitBridge bridge) {
        try {
            cacheUniqueMethod(HookTargets.KEY_SKIN_NIGHT_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().declaredClass("com.dragon.read.base.skin.SkinManager")
                            .name("isNightMode").modifiers(Modifier.STATIC).paramCount(0).returnType(Boolean.TYPE))));
            cacheUniqueMethod(HookTargets.KEY_SKIN_CHANGE_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().declaredClass("com.dragon.read.base.skin.SkinManager")
                            .name("changeSkinType").modifiers(Modifier.STATIC)
                            .paramTypes("com.dragon.read.base.skin.base.Skin").returnType(Void.TYPE))));
        } catch (Throwable error) { log("查找宿主皮肤入口失败", error); }
        try {
            cacheUniqueMethod(HookTargets.KEY_SETTINGS_PAGE_CREATE_METHOD,
                    bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                            .declaredClass("com.dragon.read.component.biz.impl.mine.settings.SettingsActivity")
                            .name("onCreate").paramTypes("android.os.Bundle").returnType(Void.TYPE)
                            .addUsingString("settings_preload")
                            .addInvoke("Landroidx/recyclerview/widget/RecyclerView;->setAdapter(Landroidx/recyclerview/widget/RecyclerView$Adapter;)V"))));
        } catch (Throwable throwable) {
            log("查找插件设置页面承载入口失败", throwable);
        }
        try {
            List<MethodData> landing = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().name("<init>")
                            .paramTypes("com.dragon.read.base.AbsActivity")
                            .addUsingString("setting_feed_landing_category")
                            .addUsingString("默认启动页面")));
            if (landing.size() != 1) {
                log("设置锚点模型不唯一，候选数=" + landing.size());
                return;
            }
            // 通用设置项是锚点构造器直接调用的唯一无参父构造器，不写死混淆类名。
            List<MethodData> bases = new ArrayList<>();
            for (MethodData call : landing.get(0).getInvokes()) {
                if ("<init>".equals(call.getName()) && call.getParamTypeNames().isEmpty()
                        && !"java.lang.Object".equals(call.getDeclaredClassName())) {
                    bases.add(call);
                }
            }
            if (bases.size() != 1) {
                log("通用设置项父构造器不唯一，候选数=" + bases.size());
                return;
            }
            cacheClass(HookTargets.KEY_SETTINGS_ITEM_CLASS, bases.get(0).getDeclaredClass());
            String constructor = landing.get(0).getDescriptor();
            cacheUniqueMethod(HookTargets.KEY_SETTINGS_NATIVE_INSERT_METHOD,
                    bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                            .declaredClass("com.dragon.read.component.biz.impl.mine.settings.SettingsActivity")
                            .paramTypes("java.util.List", "java.util.List").returnType(Void.TYPE)
                            .addUsingString("addLandingSelectItemIfNeeded: invalid list state")
                            .addInvoke(constructor))));
            cacheUniqueMethod(HookTargets.KEY_SETTINGS_KMP_INSERT_METHOD,
                    bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                    .paramTypes("java.util.LinkedList", "java.util.ArrayList")
                                    .returnType(Void.TYPE).addInvoke(constructor))));
        } catch (Throwable throwable) {
            log("查找插件设置入口目标失败", throwable);
        }
    }

    private void findCategoryTabTargets(DexKitBridge bridge) {
        try {
            cacheUniqueMethod(HookTargets.KEY_MAIN_HOME_TARGET_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass("com.dragon.read.pages.main.MainFragmentActivity")
                            .paramCount(0).returnType("com.dragon.read.rpc.model.BottomTabBarItemType")
                            .usingStrings(new String[]{"getTargetTabType: tabName %s, index %d", "key_default_tab"}))));
        } catch (Throwable throwable) {
            log("查找主页目标路由失败", throwable);
        }
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
            List<MethodData> consumers = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().name("invoke").paramTypes("java.lang.Object")
                            .returnType("java.lang.Object")
                            .addUsingField("Lcom/dragon/read/rpc/model/GetBookShelfBannerResponse;->data:Lcom/dragon/read/rpc/model/GetBookShelfBannerData;", UsingType.Read)
                            .addUsingField("Lcom/dragon/read/rpc/model/GetBookShelfBannerData;->bannerData:Ljava/util/List;", UsingType.Read)));
            List<MethodData> main = new ArrayList<>(), list = new ArrayList<>(), first = new ArrayList<>();
            for (MethodData consumer : consumers) {
                if (invokes(consumer, "Lcom/dragon/read/util/NetReqUtil;->assertRspDataOk(Ljava/lang/Object;)V")) {
                    main.add(consumer);
                } else if (invokes(consumer, "Lkotlin/collections/CollectionsKt;->firstOrNull(Ljava/util/List;)Ljava/lang/Object;")) {
                    first.add(consumer);
                } else if (invokes(consumer, "Ljava/util/List;->isEmpty()Z")
                        && invokes(consumer, "Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z")) {
                    list.add(consumer);
                }
            }
            cacheUniqueMethod(HookTargets.KEY_BOOKSHELF_BANNER_RESPONSE_METHOD, main);
            cacheUniqueMethod(HookTargets.KEY_BOOKSHELF_BANNER_LIST_METHOD, list);
            cacheUniqueMethod(HookTargets.KEY_BOOKSHELF_BANNER_FIRST_METHOD, first);
        } catch (Throwable throwable) {
            log("查找书架 Banner 响应方法失败", throwable);
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
            findQuickAccessModelClasses(bridge, converter);

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

    private static boolean invokes(MethodData method, String descriptor) {
        for (MethodData call : method.getInvokes()) {
            if (descriptor.equals(call.getDescriptor())) {
                return true;
            }
        }
        return false;
    }

    private void cacheUniqueMethod(String key, List<MethodData> methods) {
        if (methods.size() == 1 && methods.get(0).isMethod()) {
            cacheMethod(key, methods.get(0));
        } else {
            log(key + " 无法唯一定位，候选数=" + methods.size());
        }
    }

    private void findQuickAccessModelClasses(DexKitBridge bridge, MethodData converter) {
        List<MethodData> history = new ArrayList<>(), download = new ArrayList<>();
        List<MethodData> message = new ArrayList<>(), follow = new ArrayList<>();
        for (MethodData ctor : converter.getInvokes()) {
            if (!"<init>".equals(ctor.getName())) {
                continue;
            }
            List<String> params = ctor.getParamTypeNames();
            if (ctor.getUsingStrings().contains("浏览历史")) {
                history.add(ctor);
            } else if (ctor.getUsingStrings().contains("我的下载")) {
                download.add(ctor);
            } else if (params.size() == 2
                    && "androidx.fragment.app.FragmentActivity".equals(params.get(0))
                    && invokes(ctor, "Ljava/lang/Number;->intValue()I")) {
                // 宿主 dispatcher 提供标题/图标/Runnable 的动态消息入口。
                message.add(ctor);
            }
            for (MethodData nested : ctor.getInvokes()) {
                String owner = ctor.getDeclaredClassName();
                String packageName = owner.substring(0, owner.lastIndexOf('.') + 1);
                if (!"<init>".equals(nested.getName())
                        || !nested.getDeclaredClassName().startsWith(packageName)) {
                    continue;
                }
                boolean found = false;
                for (MethodData click : nested.getDeclaredClass().getMethods()) {
                    if (invokes(click, "Lcom/dragon/read/component/biz/api/NsMineDepend;->clickMyFollowItem(Landroid/app/Activity;)V")) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    follow.add(ctor);
                    break;
                }
            }
        }
        cacheUniqueModel(HookTargets.KEY_QUICK_HISTORY_CLASS, history);
        cacheUniqueModel(HookTargets.KEY_QUICK_DOWNLOAD_CLASS, download);
        cacheUniqueModel(HookTargets.KEY_QUICK_MESSAGE_CLASS, message);
        cacheUniqueModel(HookTargets.KEY_QUICK_FOLLOW_CLASS, follow);
    }

    private void cacheUniqueModel(String key, List<MethodData> constructors) {
        if (constructors.size() == 1) {
            cacheClass(key, constructors.get(0).getDeclaredClass());
        } else {
            log(key + " 模型无法唯一定位，候选数=" + constructors.size());
        }
    }

    private void findKmpMyPageTargets(DexKitBridge bridge) {
        try {
            List<MethodData> content = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.kmp.mine.tab.page.topbar"})
                    .matcher(MethodMatcher.create().name("invoke").paramCount(3)
                            .returnType("java.lang.Object")
                            .addUsingString("FanqieMineTopBar.<anonymous> (FanqieMineTopBar.kt:")));
            if (content.size() != 1) {
                log("KMP 我的页头部内容不唯一，候选=" + content.size());
                return;
            }
            cacheClass(HookTargets.KEY_MY_PAGE_KMP_TOPBAR_CLASS, content.get(0).getDeclaredClass());
            // 头部构造参数会选择宿主自己的空白占位分支，无需替换 Compose 渲染。
        } catch (Throwable throwable) {
            log("查找 KMP 我的页头部目标失败", throwable);
        }
        try {
            List<MethodData> quick = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.kmp.mine.tab.quickaccess"})
                    .matcher(MethodMatcher.create().paramCount(2).returnType(Void.TYPE)
                            .addUsingString("duplicate item=")
                            .addUsingString("Factory returned mismatched item ")));
            if (quick.size() == 1 && "java.util.List".equals(quick.get(0).getParamTypeNames().get(0))) {
                cacheMethod(HookTargets.KEY_MY_PAGE_KMP_QUICK_METHOD, quick.get(0));
            } else {
                log("KMP 快捷入口无法唯一定位，候选=" + quick.size());
            }
        } catch (Throwable throwable) {
            log("查找 KMP 快捷入口失败", throwable);
        }
    }

    private void findMyPageViewTargets(DexKitBridge bridge) {
        try {
            List<MethodData> pages = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create()
                            .declaredClass("com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2")
                            .name("onCreateContent")
                            .paramTypes("android.view.LayoutInflater", "android.view.ViewGroup", "android.os.Bundle")
                            .returnType("android.view.View")
                            .addUsingString("mine_search_entrance_config_v683")));
            List<MethodData> factories = new ArrayList<>();
            if (pages.size() == 1) {
                for (MethodData call : pages.get(0).getInvokes()) {
                    List<String> params = call.getParamTypeNames();
                    if (Modifier.isStatic(call.getModifiers()) && params.size() == 2
                            && "android.content.Context".equals(params.get(0))
                            && "androidx.compose.ui.platform.ComposeView".equals(call.getReturnTypeName())
                            && invokes(call, "Landroidx/compose/ui/platform/ComposeView;->setContent(Lkotlin/jvm/functions/Function2;)V")) {
                        factories.add(call);
                    }
                }
            }
            cacheUniqueMethod(HookTargets.KEY_MY_PAGE_SEARCH_FACTORY_METHOD, factories);
        } catch (Throwable throwable) {
            log("查找我的页搜索 View 工厂失败", throwable);
        }
        try {
            List<ClassData> wallets = bridge.findClass(FindClass.create()
                    .searchPackages(new String[]{"com.dragon.read.component.biz.impl.mine.card.model"})
                    .matcher(ClassMatcher.create().usingStrings("PolarisCard", "key_withdraw_cache")));
            if (wallets.size() == 1) {
                cacheClass(HookTargets.KEY_MY_PAGE_WALLET_CLASS, wallets.get(0));
            } else {
                log("钱包卡组件无法唯一定位，候选数=" + wallets.size());
            }
        } catch (Throwable throwable) {
            log("查找钱包卡组件失败", throwable);
        }
    }

    private void findRecommendParseMethods(DexKitBridge bridge) {
        try {
            List<MethodData> nativeMethods = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.component.biz.impl.bookmall"})
                    .matcher(MethodMatcher.create().modifiers(Modifier.STATIC)
                            .paramTypes("com.dragon.read.rpc.model.CellViewData", "int", "int",
                                    "com.dragon.read.feed.staggeredfeed.FeedScene")
                            .returnType(List.class)
                            .addUsingString("infinite_preference_card_show_time")
                            .addUsingString("parseTopicInfoFromAbstractTags error: ")));
            cacheUniqueMethod(HookTargets.KEY_RECOMMEND_NATIVE_PARSE_METHOD, nativeMethods);
            if (nativeMethods.size() == 1) {
                List<MethodData> mallMethods = bridge.findMethod(FindMethod.create().matcher(
                        MethodMatcher.create().declaredClass(nativeMethods.get(0).getDeclaredClassName())
                                .modifiers(Modifier.STATIC)
                                .paramTypes("com.dragon.read.rpc.model.CellViewData", "int", "int",
                                        "com.dragon.read.rpc.model.ClientTemplate", "boolean")
                                .returnType(List.class)
                                .addUsingField("Lcom/dragon/read/rpc/model/CellViewData;->renderCellType:Lcom/dragon/read/rpc/model/RenderCellType;", UsingType.Read)
                                .addUsingString("解析服务端定位参数成功")));
                cacheUniqueMethod(HookTargets.KEY_RECOMMEND_MALL_PARSE_METHOD, mallMethods);
            }
        } catch (Throwable throwable) {
            log("查找原生推荐场景解析入口失败", throwable);
        }
        try {
            List<MethodData> kmpMethods = bridge.findMethod(FindMethod.create()
                    .searchPackages(new String[]{"com.dragon.read.kmp.common_feed.data"})
                    .matcher(MethodMatcher.create().modifiers(Modifier.STATIC)
                            .paramTypes("com.dragon.read.rpc.model.CellViewData", "java.lang.Integer", "boolean",
                                    "com.dragon.read.kmp.common_feed.data.CellViewDataParseManager$CardParsePolicy",
                                    "com.dragon.read.feed.staggeredfeed.FeedScene")
                            .returnType(List.class)
                            .addUsingField("Lcom/dragon/read/rpc/model/CellViewData;->cellData:Ljava/util/List;", UsingType.Read)
                            .addUsingField("Lcom/dragon/read/rpc/model/CellViewData;->groupIdType:Lcom/dragon/read/rpc/model/CandidateDataType;", UsingType.Read)
                            .addInvoke("Lcom/dragon/read/rpc/model/ShowType;->getValue()I")));
            cacheUniqueMethod(HookTargets.KEY_RECOMMEND_KMP_PARSE_METHOD, kmpMethods);
        } catch (Throwable throwable) {
            log("查找 KMP 推荐场景解析入口失败", throwable);
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

    private void findMineRedDotTargets(DexKitBridge bridge) {
        try {
            cacheUniqueMethod(HookTargets.KEY_MINE_SIDEBAR_BADGE_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass("com.dragon.read.component.biz.impl.mine.sidebar.SidebarPageDataManager")
                            .modifiers(Modifier.STATIC).paramTypes("java.util.List").returnType(List.class)
                            .addUsingField("Lcom/dragon/read/rpc/model/MsgGroup;->showUnreadCount:Z", UsingType.Read)
                            .addUsingField("Lcom/dragon/read/rpc/model/MsgGroup;->unreadCount:I", UsingType.Read))));
        } catch (Throwable error) {
            log("查找侧栏未读角标展示转换失败", error);
        }
        try {
            cacheUniqueMethod(HookTargets.KEY_MINE_RED_DOT_GATE_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass("com.dragon.read.kmp.mine.reddot.KmpMineRedDotManager")
                            .modifiers(Modifier.STATIC).paramTypes(Boolean.TYPE).returnType(Boolean.TYPE)
                            .addUsingString("命中新用户保护策略，不展示红点")
                            .addUsingString("命中老用户保护策略，不展示红点"))));
        } catch (Throwable error) {
            log("查找我的页共用角标展示门禁失败", error);
        }
        try {
            cacheUniqueMethod(HookTargets.KEY_BOTTOM_BADGE_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().paramTypes("int", "java.lang.String").returnType(Void.TYPE)
                            .addUsingString("开宝箱").addUsingString("赚钱").addUsingString("签到")
                            .addInvoke("Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V")
                            .addInvoke("Lcom/dragon/read/util/UiUtils;->setVisibility(Landroid/view/View;I)V"))));
        } catch (Throwable error) {
            log("查找底栏数字文字角标展示入口失败", error);
        }
        try {
            cacheUniqueMethod(HookTargets.KEY_MINE_TAB_BADGE_METHOD, bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass("com.dragon.read.pages.main.MainFragmentActivity")
                            .paramCount(1).returnType(Void.TYPE)
                            .addUsingString("MINE_RED_DOT_FILTER")
                            .addUsingString("TAB_MINE showpic")
                            .addUsingString("TAB_MINE showBubbleText"))));
        } catch (Throwable error) {
            log("查找我的底栏角标聚合入口失败", error);
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
