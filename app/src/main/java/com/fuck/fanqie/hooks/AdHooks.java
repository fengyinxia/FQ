package com.fuck.fanqie.hooks;

import android.view.View;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.MethodCacheManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AdHooks extends BaseHook {
    public AdHooks(MethodCacheManager cacheManager, ClassLoader hostClassLoader) {
        super(cacheManager, hostClassLoader);
    }

    @Override
    public void apply() {
        applyAdHooks();
        applyLuckyDogHooks();
        applyHideBannerHooks();
        applyFloatingViewHooks();
        applyClickAgentHooks();
    }

    public void applyAdHooks() {
        try {
            Method adConfigMethod = cacheManager.getMethod(HookTargets.KEY_AD_CONFIG_METHOD);
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
            Method adFreeMethod = cacheManager.getMethod(HookTargets.KEY_AD_FREE_METHOD);
            if (adFreeMethod != null) {
                XposedBridge.hookMethod(adFreeMethod, XC_MethodReplacement.returnConstant(Boolean.TRUE));
                XposedBridge.log("FQHook+Ad: 已启用广告免除(Method)");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Ad: 启用广告免除(Method)失败: ", throwable);
        }

        try {
            Class<?> adFreeClass = cacheManager.getClass(HookTargets.KEY_AD_FREE_CLASS);
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
        Method luckyDogMethod = cacheManager.getMethod(HookTargets.KEY_LUCKY_DOG_METHOD);
        if (luckyDogMethod != null) {
            XposedBridge.hookMethod(luckyDogMethod, XC_MethodReplacement.returnConstant(Boolean.FALSE));
            XposedBridge.log("FQHook+applyHooks: 已禁用LuckyDog福利");
        }
    }

    public void applyHideBannerHooks() {
        try {
            Method filterBannerMethod = cacheManager.getMethod(HookTargets.KEY_FILTER_BANNER_METHOD);
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

    public void applyFloatingViewHooks() {
        Method popMethod = cacheManager.getMethod(HookTargets.KEY_POP_METHOD);
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
