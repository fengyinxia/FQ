package com.fuck.fanqie.hooks;

import android.view.View;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AdHooks extends BaseHook {
    private final CachedTargets cachedTargets;

    public AdHooks(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        super(hostClassLoader);
        this.cachedTargets = cachedTargets;
    }

    @Override
    public void apply() {
        applyAdHooks();
        applyLuckyDogHooks();
        applyHideBannerHooks();
        applyHideBookshelfRelateVideoBannerHooks();
        applyFloatingViewHooks();
        applyClickAgentHooks();
    }

    public void applyAdHooks() {
        try {
            Method adConfigMethod = cachedTargets.method(HookTargets.KEY_AD_CONFIG_METHOD);
            if (adConfigMethod != null) {
                XposedBridge.hookMethod(adConfigMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if (key != null && key.contains("_ad")) {
                            param.setResult(Boolean.FALSE);
                        }
                    }
                });
                XposedBridge.log("FQHook+Ad: 已禁用广告配置");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Ad: 禁用广告配置失败: ", throwable);
        }

        try {
            Method adFreeMethod = cachedTargets.method(HookTargets.KEY_AD_FREE_METHOD);
            if (adFreeMethod != null) {
                XposedBridge.hookMethod(adFreeMethod, XC_MethodReplacement.returnConstant(Boolean.TRUE));
                XposedBridge.log("FQHook+Ad: 已启用广告免除(Method)");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Ad: 启用广告免除(Method)失败: ", throwable);
        }

        try {
            Class<?> adFreeClass = cachedTargets.type(HookTargets.KEY_AD_FREE_CLASS);
            if (adFreeClass == null) {
                XposedBridge.log("FQHook+Ad: 未找到广告免除类 (KEY_AD_FREE_CLASS)，跳过相关Hook");
            } else {
                for (String methodName : new String[]{
                        "isVip",
                        "hasNoAdFollAllScene",
                        "isAnyVip",
                        "hasNoAdPrivilege",
                        "adVipAvailable"
                }) {
                    try {
                        XposedHelpers.findAndHookMethod(
                                adFreeClass,
                                methodName,
                                XC_MethodReplacement.returnConstant(Boolean.TRUE)
                        );
                        XposedBridge.log("FQHook+Ad: 已Hook方法 " + methodName + " 返回 true");
                    } catch (Throwable throwable) {
                        XposedBridge.log("FQHook+Ad: Hook方法 " + methodName + " 失败: " + throwable.getMessage());
                    }
                }

                try {
                    XposedHelpers.findAndHookMethod(
                            adFreeClass,
                            "isNoAd",
                            String.class,
                            XC_MethodReplacement.returnConstant(Boolean.TRUE)
                    );
                    XposedBridge.log("FQHook+Ad: 已Hook isNoAd 返回 true");
                } catch (Throwable throwable) {
                    XposedBridge.log("FQHook+Ad: Hook isNoAd 失败: " + throwable.getMessage());
                }
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Ad: 处理广告免除类时出错: ", throwable);
        }
        XposedBridge.log("FQHook+Ad: 广告Hook应用完成");
    }

    public void applyLuckyDogHooks() {
        Method luckyDogMethod = cachedTargets.method(HookTargets.KEY_LUCKY_DOG_METHOD);
        if (luckyDogMethod != null) {
            XposedBridge.hookMethod(luckyDogMethod, XC_MethodReplacement.returnConstant(Boolean.FALSE));
            XposedBridge.log("FQHook+applyHooks: 已禁用LuckyDog福利");
        }
    }

    public void applyHideBannerHooks() {
        try {
            Method filterBannerMethod = cachedTargets.method(HookTargets.KEY_FILTER_BANNER_METHOD);
            if (filterBannerMethod == null) {
                XposedBridge.log("FQHook+Banner: 未找到 Banner 相关方法");
                return;
            }
            XposedBridge.hookMethod(filterBannerMethod, new XC_MethodHook() {
                private volatile Field cachedPictureDataField;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object target = param.args[0];
                    if (target == null) {
                        return;
                    }
                    Field pictureDataField = cachedPictureDataField;
                    if (pictureDataField == null) {
                        pictureDataField = target.getClass().getDeclaredField("pictureData");
                        pictureDataField.setAccessible(true);
                        cachedPictureDataField = pictureDataField;
                    }
                    pictureDataField.set(target, new ArrayList<>());
                }
            });
            XposedBridge.log("FQHook+Banner: 成功应用 Banner 隐藏钩子");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Banner: 应用 Banner 隐藏钩子失败: ", throwable);
        }
    }

    public void applyHideBookshelfRelateVideoBannerHooks() {
        try {
            Method bannerResponseMethod = cachedTargets.method(HookTargets.KEY_BOOKSHELF_BANNER_RESPONSE_METHOD);
            if (bannerResponseMethod == null) {
                XposedBridge.log("FQHook+BookshelfBanner: 未找到书架 Banner 响应方法");
                return;
            }
            XposedBridge.hookMethod(bannerResponseMethod, new XC_MethodHook() {
                private volatile Field cachedDataField;
                private volatile Field cachedBannerDataField;
                private volatile Field cachedBannerTypeField;

                @Override
                @SuppressWarnings("unchecked")
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object response = param.args[0];
                    Object data = readField(response, "data", true);
                    if (data == null) {
                        return;
                    }
                    Object bannerData = readField(data, "bannerData", false);
                    if (!(bannerData instanceof List<?>)) {
                        return;
                    }
                    List<Object> original = (List<Object>) bannerData;
                    if (original.isEmpty()) {
                        return;
                    }

                    ArrayList<Object> filtered = new ArrayList<>(original.size());
                    int removedCount = 0;
                    for (Object item : original) {
                        if (isRelateVideoBanner(item)) {
                            removedCount++;
                            continue;
                        }
                        filtered.add(item);
                    }
                    if (removedCount == 0) {
                        return;
                    }
                    cachedBannerDataField.set(data, filtered);
                    XposedBridge.log("FQHook+BookshelfBanner: 已过滤书架短剧 Banner " + removedCount + " 条");
                }

                private Object readField(Object target, String fieldName, boolean isResponseField) throws Throwable {
                    if (target == null) {
                        return null;
                    }
                    Field field = resolveField(target.getClass(), fieldName, isResponseField);
                    return field.get(target);
                }

                private Field resolveField(Class<?> type, String fieldName, boolean isResponseField) throws Throwable {
                    Field field = isResponseField ? cachedDataField : cachedBannerDataField;
                    if (field == null) {
                        field = type.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        if (isResponseField) {
                            cachedDataField = field;
                        } else {
                            cachedBannerDataField = field;
                        }
                    }
                    return field;
                }

                private boolean isRelateVideoBanner(Object item) throws Throwable {
                    if (item == null) {
                        return false;
                    }
                    Field bannerTypeField = cachedBannerTypeField;
                    if (bannerTypeField == null) {
                        bannerTypeField = item.getClass().getDeclaredField("bannerType");
                        bannerTypeField.setAccessible(true);
                        cachedBannerTypeField = bannerTypeField;
                    }
                    Object bannerType = bannerTypeField.get(item);
                    return bannerType instanceof Enum<?> && "RelateVideo".equals(((Enum<?>) bannerType).name());
                }
            });
            XposedBridge.log("FQHook+BookshelfBanner: 成功应用书架短剧 Banner 过滤钩子");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+BookshelfBanner: 应用书架短剧 Banner 过滤钩子失败: ", throwable);
        }
    }

    public void applyFloatingViewHooks() {
        Method popMethod = cachedTargets.method(HookTargets.KEY_POP_METHOD);
        if (popMethod != null) {
            XposedBridge.hookMethod(popMethod, XC_MethodReplacement.returnConstant(Boolean.TRUE));
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.dragon.read.pop.absettings.a",
                    hostClassLoader,
                    "a",
                    XC_MethodReplacement.returnConstant(Boolean.TRUE)
            );
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyFloatingViewHooks: Hook com.dragon.read.pop.absettings.a 失败: ", throwable);
        }
        XposedBridge.log("FQHook+applyFloatingViewHooks: 已禁用弹窗");
    }

    public void applyClickAgentHooks() {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.bytedance.apm.agent.v2.instrumentation.ClickAgent",
                    hostClassLoader,
                    "onTabChanged",
                    String.class,
                    XC_MethodReplacement.returnConstant(null)
            );
            XposedHelpers.findAndHookMethod(
                    "com.bytedance.apm.agent.v2.instrumentation.ClickAgent",
                    hostClassLoader,
                    "onClick",
                    View.class,
                    XC_MethodReplacement.returnConstant(null)
            );
            XposedBridge.log("FQHook+ClickAgent: 已禁用字节跳动点击统计");
        } catch (Throwable throwable) {
            XposedBridge.log("FQHook+ClickAgent: Hook失败: " + throwable.getMessage());
        }
    }
}
