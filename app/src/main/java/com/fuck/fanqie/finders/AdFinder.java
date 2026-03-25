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

public class AdFinder extends BaseFinder {
    public AdFinder(TargetScanResult scanResult) {
        super(scanResult);
    }

    @Override
    public void find(DexKitBridge bridge) {
        findAdRelatedTargets(bridge);
        findLuckyDogMethod(bridge);
        findPopMethod(bridge);
    }

    private void findAdRelatedTargets(DexKitBridge bridge) {
        try {
            ClassData adConfigClass = first(bridge.findClass(
                    FindClass.create().matcher(
                            ClassMatcher.create().usingStrings(new String[]{
                                    "checkAdAvailableWithoutSource",
                                    "AdConfigManager"
                            })
                    )
            ));
            if (adConfigClass != null) {
                MethodData adConfigMethod = first(bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .declaredClass(adConfigClass.getName())
                                        .paramTypes(new String[]{String.class.getName(), String.class.getName()})
                                        .returnType(Boolean.TYPE)
                        )
                ));
                cacheMethod(HookTargets.KEY_AD_CONFIG_METHOD, adConfigMethod);
            }

            try {
                ClassData adFreeClass = first(bridge.findClass(
                        FindClass.create().matcher(
                                ClassMatcher.create().usingStrings(new String[]{
                                        "所有场景免广告权益有变化"
                                })
                        )
                ));
                cacheClass(HookTargets.KEY_AD_FREE_CLASS, adFreeClass);
                if (adFreeClass != null) {
                    MethodData adFreeMethod = first(bridge.findMethod(
                            FindMethod.create().matcher(
                                    MethodMatcher.create()
                                            .declaredClass(adFreeClass.getName())
                                            .usingStrings(new String[]{"1"})
                                            .paramCount(1)
                                            .returnType(Boolean.TYPE)
                            )
                    ));
                    cacheMethod(HookTargets.KEY_AD_FREE_METHOD, adFreeMethod);
                }
            } catch (Throwable throwable) {
                log("查找免广告方法失败", throwable);
            }
        } catch (Throwable throwable) {
            log("查找广告配置方法失败", throwable);
        }
    }

    private void findLuckyDogMethod(DexKitBridge bridge) {
        try {
            MethodData luckyDogMethod = first(bridge.findMethod(
                    FindMethod.create()
                            .searchPackages(new String[]{"com.dragon.read"})
                            .matcher(
                                    MethodMatcher.create()
                                            .usingStrings(new String[]{"gold_reverse", "app_global_config"})
                                            .returnType(Boolean.TYPE)
                                            .paramCount(0)
                            )
            ));
            cacheMethod(HookTargets.KEY_LUCKY_DOG_METHOD, luckyDogMethod);
        } catch (Throwable throwable) {
            log("查找 LuckyDog 方法失败", throwable);
        }
    }

    private void findPopMethod(DexKitBridge bridge) {
        try {
            MethodData popMethod = first(bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create().usingStrings(new String[]{
                                    "调试模式，跳过所有弹窗限制"
                            })
                    )
            ));
            cacheMethod(HookTargets.KEY_POP_METHOD, popMethod);
        } catch (Throwable throwable) {
            log("查找弹窗方法失败", throwable);
        }
    }
}
