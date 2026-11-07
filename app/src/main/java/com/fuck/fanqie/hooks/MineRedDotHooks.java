package com.fuck.fanqie.hooks;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.PluginSettings;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** 只控制角标展示，不修改消息计数、已读状态、广播或服务端响应。 */
final class MineRedDotHooks {
    private final CachedTargets targets;

    MineRedDotHooks(CachedTargets targets) {
        this.targets = targets;
    }

    void apply() {
        if (!PluginSettings.enabled("red_dot")) return;
        install(HookTargets.KEY_RED_DOT_METHOD, 0);
        install(HookTargets.KEY_MINE_RED_DOT_GATE_METHOD, 1);
        install(HookTargets.KEY_BOTTOM_BADGE_METHOD, 2);
        install(HookTargets.KEY_MINE_TAB_BADGE_METHOD, 3);
        install(HookTargets.KEY_MINE_SIDEBAR_BADGE_METHOD, 4);
    }

    private void install(String key, final int kind) {
        try {
            Method method = targets.method(key);
            if (method == null) {
                XposedBridge.log("FQHook+RedDot: 目标缺失，跳过 " + key);
                return;
            }
            Class<?>[] params = method.getParameterTypes();
            boolean valid;
            switch (kind) {
                case 0:
                    valid = method.getReturnType() == void.class && params.length == 1
                            && params[0] == boolean.class && !Modifier.isStatic(method.getModifiers());
                    break;
                case 1:
                    valid = method.getReturnType() == boolean.class && params.length == 1
                            && params[0] == boolean.class && Modifier.isStatic(method.getModifiers());
                    break;
                case 2:
                    valid = method.getReturnType() == void.class && params.length == 2
                            && params[0] == int.class && params[1] == String.class
                            && !Modifier.isStatic(method.getModifiers());
                    break;
                case 4:
                    valid = method.getReturnType() == List.class && params.length == 1
                            && params[0] == List.class && Modifier.isStatic(method.getModifiers());
                    break;
                default:
                    valid = method.getReturnType() == void.class && params.length == 1
                            && !params[0].isPrimitive() && !Modifier.isStatic(method.getModifiers())
                            && "com.dragon.read.pages.main.MainFragmentActivity".equals(method.getDeclaringClass().getName());
            }
            if (!valid) {
                XposedBridge.log("FQHook+RedDot: 签名不兼容，跳过 " + key + ": " + method);
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                private int logged;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    switch (kind) {
                        case 0:
                            param.args[0] = Boolean.FALSE;
                            break;
                        case 1:
                            // 原生大/小网格和 KMP 快捷入口在渲染前均使用此展示门禁。
                            param.setResult(Boolean.FALSE);
                            break;
                        case 2:
                            // 空字符串走宿主 GONE 分支，避免 View 复用留下旧数字/文字角标。
                            param.args[1] = "";
                            break;
                        case 4:
                            try {
                                param.args[0] = MineBadgeFilter.sidebar(param.args[0]);
                            } catch (Throwable error) {
                                HookUtils.logError("FQHook+RedDot: 侧栏展示副本处理失败，保留宿主: ", error);
                            }
                            break;
                        default:
                            // 原方法先清理我的 Tab 旧角标，null 随后正常返回，覆盖 PIC 分支。
                            param.args[0] = null;
                    }
                    if (logged++ < 3) XposedBridge.log("FQHook+RedDot: 已抑制角标展示 " + key);
                }
            });
            XposedBridge.log("FQHook+RedDot: 已安装 " + key + ": " + method);
        } catch (Throwable error) {
            HookUtils.logError("FQHook+RedDot: 安装失败 " + key + ": ", error);
        }
    }
}
