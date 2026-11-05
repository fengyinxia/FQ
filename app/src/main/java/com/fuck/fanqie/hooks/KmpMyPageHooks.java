package com.fuck.fanqie.hooks;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.PluginSettings;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** KMP 页独立目标，不复用原生页 key，不拦截整页 Compose。 */
final class KmpMyPageHooks extends BaseHook {
    private final CachedTargets targets;

    KmpMyPageHooks(CachedTargets targets, ClassLoader loader) {
        super(loader);
        this.targets = targets;
    }

    @Override
    public void apply() {
        if (PluginSettings.any("search_mine", "sidebar")) installTopBar();
        installQuickAccess(); // 发现链常驻，用户不屏蔽时也更新目录。
    }

    private void installTopBar() {
        try {
            Class<?> content = targets.type(HookTargets.KEY_MY_PAGE_KMP_TOPBAR_CLASS);
            if (content == null) {
                XposedBridge.log("FQHook+KmpMine: 头部内容类缺失，跳过");
                return;
            }
            // 构造参数为 showSearchButton/model/scanLabel/searchLabel/compress/style1/sidebar/sidebarAction。
            // 保持宿主布局和扫码、主题分支，仅关闭搜索并选原生 settings 分支。
            int count = 0;
            for (java.lang.reflect.Constructor<?> ctor : content.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length != 8 || p[0] != boolean.class || p[2] != String.class || p[3] != String.class
                        || p[4] != boolean.class || p[5] != boolean.class || p[6] != boolean.class
                        || !"kotlin.jvm.functions.Function0".equals(p[7].getName())) {
                    continue;
                }
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    private boolean logged;

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // false/false/false 进入宿主左侧 weight Spacer，搜索不绘制且保留头部空间。
                        if (PluginSettings.enabled("search_mine")) {
                            param.args[0] = false;
                            param.args[4] = false;
                            param.args[5] = false;
                        }
                        if (PluginSettings.enabled("sidebar")) param.args[6] = false;
                        if (!logged) {
                            logged = true;
                            XposedBridge.log("FQHook+KmpMine: 已按配置处理头部，隐藏搜索="
                                    + PluginSettings.enabled("search_mine") + "，设置入口=" + PluginSettings.enabled("sidebar"));
                        }
                    }
                });
                count++;
            }
            XposedBridge.log("FQHook+KmpMine: 已安装头部构造入口 " + count);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+KmpMine: 头部 Hook 失败: ", throwable);
        }
    }

    private void installQuickAccess() {
        try {
            Method quick = targets.method(HookTargets.KEY_MY_PAGE_KMP_QUICK_METHOD);
            if (quick == null || quick.getReturnType() != void.class
                    || quick.getParameterTypes().length != 2 || quick.getParameterTypes()[0] != List.class) {
                XposedBridge.log("FQHook+KmpMine: 快捷入口契约缺失，跳过");
                return;
            }
            XposedBridge.hookMethod(quick, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof List<?>)) {
                        return;
                    }
                    try {
                        List<?> original = (List<?>) param.args[0];
                        List<Object> kept = new ArrayList<>();
                        for (Object item : original) {
                            if (!MineItemFilter.card(item)) {
                                kept.add(item);
                            }
                        }
                        if (kept.size() != original.size()) {
                            param.args[0] = kept;
                            XposedBridge.log("FQHook+KmpMine: 快捷入口 " + original.size() + "→" + kept.size()
                                    + "，按动态项目选择过滤");
                        }
                    } catch (Throwable throwable) {
                        HookUtils.logError("FQHook+KmpMine: 快捷类型识别失败，保留原列表: ", throwable);
                    }
                }
            });
            XposedBridge.log("FQHook+KmpMine: 已安装快捷源列表过滤: " + quick);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+KmpMine: 快捷入口 Hook 失败: ", throwable);
        }
    }
}
