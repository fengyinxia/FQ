package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FeatureHooks extends BaseHook {
    private final CachedTargets cachedTargets;

    public FeatureHooks(CachedTargets cachedTargets, ClassLoader hostClassLoader) {
        super(hostClassLoader);
        this.cachedTargets = cachedTargets;
    }

    @Override
    public void apply() {
        applyAbtestHooks();
        applySplashK1Hook();
        applyReaderBackHooks();
        applyPopProxyHooks();
        applyUpdateHooks();
        applyChapterControlHooks();
    }

    public void applyAbtestHooks() {
        try {
            Method method = cachedTargets.method(HookTargets.KEY_ABTEST_METHOD);
            if (method == null) {
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(null);
                }
            });
            XposedBridge.log("FQHook: 已禁用AB测试功能");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyAbtestHooks: 禁用AB测试功能失败: ", throwable);
        }
    }

    public void applySplashK1Hook() {
        try {
            Method method = cachedTargets.method(HookTargets.KEY_SPLASH_K1_METHOD);
            if (method == null) {
                XposedBridge.log("FQHook+applySplashK1Hook: 未找到启动页跳转方法(K1)，跳过Hook");
                return;
            }

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Intent intent = (Intent) param.args[0];
                    if (intent == null) {
                        return;
                    }
                    Uri data = intent.getData();
                    String tabName = intent.getExtras() == null ? null : intent.getExtras().getString("tabName");
                    if (data != null
                            && "dragon1967".equals(data.getScheme())
                            && "main".equals(data.getHost())
                            && (tabName == null || "seriesmall".equals(tabName))) {
                        intent.setClassName("com.dragon.read", "com.dragon.read.pages.main.MainFragmentActivity");
                        intent.setData(Uri.parse("dragon1967://main?tabName=bookshelf"));
                        intent.putExtra("tabName", "bookshelf");
                        intent.putExtra("page_schema", "dragon1967://main?tabName=bookshelf");
                        XposedBridge.log("FQHook+K1: 已修改为跳转书架 tab");
                    }
                }
            });
            XposedBridge.log("FQHook+applySplashK1Hook: 已Hook SplashActivity.K1");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applySplashK1Hook: Hook SplashActivity.K1 失败: ", throwable);
        }
    }

    public void applyReaderBackHooks() {
        try {
            Class<?> readerActivityClass = XposedHelpers.findClass("com.dragon.read.reader.ui.ReaderActivity", hostClassLoader);
            XposedHelpers.findAndHookMethod(readerActivityClass, "onBackPressed", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ((Activity) param.thisObject).finish();
                }
            });
            XposedBridge.log("FQHook+applyReaderBackHooks: 已Hook ReaderActivity.onBackPressed");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyReaderBackHooks: Hook ReaderActivity.onBackPressed 失败: ", throwable);
        }
    }

    public void applyPopProxyHooks() {
        try {
            Class<?> popProxyClass = XposedHelpers.findClass("com.dragon.read.pop.PopProxy", hostClassLoader);
            Class<?> propertiesClass = XposedHelpers.findClass("com.dragon.read.pop.IProperties", hostClassLoader);
            Class<?> listenerClass = XposedHelpers.findClass("com.dragon.read.pop.IPopProxy$IListener", hostClassLoader);
            int hooked = 0;
            for (Method method : popProxyClass.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!("popup".equals(method.getName()) || "enqueue".equals(method.getName()))
                        || params.length != 5 || params[0] != Activity.class
                        || params[1] != propertiesClass || params[2].isPrimitive()
                        || params[3] != listenerClass || params[4] != String.class
                        || (method.getReturnType().isPrimitive() && method.getReturnType() != void.class)) {
                    continue;
                }
                // 第三个参数已由旧 SilkRoad 类型改为 th0.b；按实际签名匹配两种弹窗调用。
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (shouldAllowPopup(param.args[1])) {
                            return;
                        }
                        Object listener = param.args[3];
                        if (listener != null) {
                            try {
                                XposedHelpers.callMethod(listener, "intercept");
                            } catch (Throwable throwable) {
                                HookUtils.logError("FQHook+PopProxy: 通知弹窗拦截失败: ", throwable);
                            }
                        }
                        param.setResult(null);
                    }
                });
                hooked++;
            }
            XposedBridge.log("FQHook+PopProxy: 已拦截 " + hooked + " 个弹窗入口 (privacy_dialog 放行)");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyPopProxyHooks: Hook PopProxy失败: ", throwable);
        }
    }

    private boolean shouldAllowPopup(Object properties) {
        if (properties == null) {
            return true;
        }
        try {
            Object privateName = XposedHelpers.callMethod(properties, "getPrivateName");
            Object id = XposedHelpers.callMethod(properties, "getID");
            return (privateName instanceof String && ((String) privateName).contains("privacy_dialog"))
                    || (id instanceof String && ((String) id).contains("privacy_dialog"));
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+PopProxy: 无法识别弹窗类型，保留原行为: ", throwable);
            return true;
        }
    }

    public void applyUpdateHooks() {
        Method updateMethod = cachedTargets.method(HookTargets.KEY_UPDATE_METHOD);
        if (updateMethod == null) {
            XposedBridge.log("FQHook+HookApplier: 未找到更新消息处理方法，跳过Hook");
        } else {
            XposedBridge.hookMethod(updateMethod, XC_MethodReplacement.returnConstant(null));
            XposedBridge.log("FQHook+HookApplier: 已应用更新消息拦截Hook");
        }

        Method checkUpdateMethod = cachedTargets.method(HookTargets.KEY_CHECK_UPDATE_METHOD);
        if (checkUpdateMethod == null) {
            XposedBridge.log("FQHook+HookApplier: 未找到检查更新方法(H0)，跳过Hook");
        } else {
            XposedBridge.hookMethod(checkUpdateMethod, XC_MethodReplacement.returnConstant(Boolean.FALSE));
            XposedBridge.log("FQHook+HookApplier: 已应用检查更新拦截Hook(H0)");
        }
    }

    private void applyCoverTitleRenderHooks() {
        try {
            final Method textMethod = cachedTargets.method(HookTargets.KEY_COVER_TEXT_RENDER_METHOD);
            if (textMethod == null || textMethod.getReturnType() != Void.TYPE
                    || java.lang.reflect.Modifier.isStatic(textMethod.getModifiers())
                    || textMethod.getParameterTypes().length != 1
                    || !"com.dragon.read.reader.bookcover.BookCoverInfo".equals(
                            textMethod.getParameterTypes()[0].getName())) {
                XposedBridge.log("FQHook+ChapterControl: 未找到兼容的封面文字渲染，跳过");
                return;
            }
            textMethod.setAccessible(true);
            XposedBridge.hookMethod(textMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args[0] != null) {
                        XposedHelpers.setObjectField(param.args[0], "bookShortName", null);
                    }
                }
            });
            XposedBridge.log("FQHook+ChapterControl: 已安装封面文字渲染: " + textMethod);

            Method imageMethod = cachedTargets.method(HookTargets.KEY_COVER_IMAGE_RENDER_METHOD);
            if (imageMethod == null || imageMethod.getReturnType() != Void.TYPE
                    || java.lang.reflect.Modifier.isStatic(imageMethod.getModifiers())
                    || imageMethod.getDeclaringClass() != textMethod.getDeclaringClass()
                    || imageMethod.getParameterTypes().length != 2
                    || imageMethod.getParameterTypes()[0] != textMethod.getParameterTypes()[0]
                    || imageMethod.getParameterTypes()[1] != Boolean.TYPE) {
                XposedBridge.log("FQHook+ChapterControl: 未找到兼容的封面图片渲染，仅保留文字清理");
                return;
            }
            XposedBridge.hookMethod(imageMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[0] == null) {
                        return;
                    }
                    // 调用已解析的宿主文字渲染，不依赖混淆类名和方法名。
                    textMethod.invoke(param.thisObject, param.args[0]);
                    param.setResult(null);
                    XposedBridge.log("FQHook+ChapterControl: 封面书名已回退为文本");
                }
            });
            XposedBridge.log("FQHook+ChapterControl: 已安装封面图片渲染: " + imageMethod);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 新版封面书名 Hook 失败: ", throwable);
        }
    }

    public void applyChapterControlHooks() {
        applyCoverTitleRenderHooks();

        try {
            Method authorSayMethod = cachedTargets.method(HookTargets.KEY_AUTHOR_SAY_METHOD);
            if (authorSayMethod != null) {
                XposedBridge.hookMethod(authorSayMethod, XC_MethodReplacement.returnConstant(null));
                XposedBridge.log("FQHook+ChapterControl: 已禁用作者说");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用作者说失败: ", throwable);
        }

        try {
            Method coverHotCommentMethod = cachedTargets.method(HookTargets.KEY_COVER_HOT_COMMENT_METHOD);
            if (coverHotCommentMethod == null) {
                XposedBridge.log("FQHook+ChapterControl: 未找到封面热门评论方法，跳过Hook");
            } else if (!View.class.isAssignableFrom(coverHotCommentMethod.getReturnType())) {
                XposedBridge.log("FQHook+ChapterControl: 封面热评返回类型不是 View，跳过: "
                        + coverHotCommentMethod);
            } else {
                // 独立热评 View 工厂本来就允许返回 null；不要再盲改混淆字段 c。
                XposedBridge.hookMethod(coverHotCommentMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                        XposedBridge.log("FQHook+ChapterControl: 已拦截封面热门评论 View 构建");
                    }
                });
                XposedBridge.log("FQHook+ChapterControl: 已安装封面热门评论 View 拦截: "
                        + coverHotCommentMethod);

                // 7.3.9.32 同一 dispatcher 的 P1 返回可空 Pair，控制 EPUB 热评页插入。
                // 在插入入口返回 null，而非跳过 Compose 渲染，避免空白热评页及状态失配。
                for (Method method : coverHotCommentMethod.getDeclaringClass().getDeclaredMethods()) {
                    if (!"P1".equals(method.getName()) || method.getParameterTypes().length != 0) {
                        continue;
                    }
                    if (!"kotlin.Pair".equals(method.getReturnType().getName())) {
                        XposedBridge.log("FQHook+ChapterControl: EPUB 封面热评插入返回类型不兼容，跳过: " + method);
                        continue;
                    }
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            XposedBridge.log("FQHook+ChapterControl: 已拦截 EPUB 封面热门评论页插入");
                        }
                    });
                    XposedBridge.log("FQHook+ChapterControl: 已安装 EPUB 封面热门评论页拦截: " + method);
                }
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用封面热门评论失败: ", throwable);
        }

        try {
            Method chapterEndHotCommentMethod = cachedTargets.method(HookTargets.KEY_CHAPTER_END_HOT_COMMENT_METHOD);
            if (chapterEndHotCommentMethod != null) {
                Class<?> resultType = chapterEndHotCommentMethod.getReturnType();
                if (resultType == boolean.class || resultType == Boolean.class) {
                    XposedBridge.hookMethod(chapterEndHotCommentMethod, XC_MethodReplacement.returnConstant(Boolean.FALSE));
                    XposedBridge.log("FQHook+ChapterControl: 已禁用章末热评");
                } else if (!resultType.isPrimitive()) {
                    // 新版返回评论组件 it6.l；原方法在数据缺失时也会返回 null。
                    XposedBridge.hookMethod(chapterEndHotCommentMethod, XC_MethodReplacement.returnConstant(null));
                    XposedBridge.log("FQHook+ChapterControl: 已禁用章末热评");
                } else {
                    XposedBridge.log("FQHook+ChapterControl: 章末热评返回类型不兼容，跳过: " + resultType.getName());
                }
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用章末热评失败: ", throwable);
        }

        try {
            Method chapterEndControlMethod = cachedTargets.method(HookTargets.KEY_CHAPTER_END_CONTROL_METHOD);
            if (chapterEndControlMethod == null) {
                XposedBridge.log("FQHook+ChapterControl: 未找到章末控件方法，跳过Hook");
            } else if ("onAttachedToWindow".equals(chapterEndControlMethod.getName())
                    && View.class.isAssignableFrom(chapterEndControlMethod.getDeclaringClass())) {
                XposedBridge.hookMethod(chapterEndControlMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        ((View) param.thisObject).setVisibility(View.GONE);
                    }
                });
                XposedBridge.log("FQHook+ChapterControl: 已隐藏章末评论开关和礼物控件");
            } else if (!chapterEndControlMethod.getReturnType().isPrimitive()
                    || chapterEndControlMethod.getReturnType() == void.class) {
                XposedBridge.hookMethod(chapterEndControlMethod, XC_MethodReplacement.returnConstant(null));
                XposedBridge.log("FQHook+ChapterControl: 已禁用旧版章末控件构建");
            } else {
                XposedBridge.log("FQHook+ChapterControl: 章末控件返回类型不兼容，跳过");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用章末控件失败: ", throwable);
        }
    }
}
