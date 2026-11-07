package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.view.View;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.PluginSettings;
import com.fuck.fanqie.DynamicItems;
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
        applyPopProxyHooks();
        applyUpdateHooks();
        applyChapterControlHooks();
    }

    public void applyPopProxyHooks() {
        try {
            Class<?> popProxyClass = XposedHelpers.findClass("com.dragon.read.pop.PopProxy", hostClassLoader);
            Class<?> propertiesClass = XposedHelpers.findClass("com.dragon.read.pop.IProperties", hostClassLoader);
            Class<?> listenerClass = XposedHelpers.findClass("com.dragon.read.pop.IPopProxy$IListener", hostClassLoader);
            Class<?> definer = XposedHelpers.findClass("com.dragon.read.pop.PopDefiner", hostClassLoader);
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
                        try {
                            Object raw = param.args[1];
                            if (protectedPopup(raw)) return;
                            Object properties = XposedHelpers.callStaticMethod(definer, "a", raw);
                            if (protectedPopup(properties)) return;
                            Object id = XposedHelpers.callMethod(properties, "getID");
                            Object name = XposedHelpers.callMethod(properties, "getPrivateName");
                            String identity = ControlItemLabels.popupIdentity(id);
                            if (identity == null || !DynamicItems.observe(DynamicItems.POPUPS, identity,
                                    ControlItemLabels.popupLabel(name, id), false)) return;
                            // 与宿主拒绝展示协议一致：通知 intercept，保留队列和完成回调语义。
                            Object listener = param.args[3];
                            if (listener != null) XposedHelpers.callMethod(listener, "intercept");
                            param.setResult(null);
                            XposedBridge.log("FQHook+PopProxy: 已按选择拦截 " + identity);
                        } catch (Throwable throwable) {
                            HookUtils.logError("FQHook+PopProxy: 无法识别或完成拦截，保留宿主: ", throwable);
                        }
                    }
                });
                hooked++;
            }
            XposedBridge.log("FQHook+PopProxy: 已安装 " + hooked + " 个动态弹窗入口（隐私/功能性弹窗放行）");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyPopProxyHooks: Hook PopProxy失败: ", throwable);
        }
    }

    private boolean protectedPopup(Object properties) {
        if (properties == null) return true;
        try {
            Object privateName = XposedHelpers.callMethod(properties, "getPrivateName");
            Object id = XposedHelpers.callMethod(properties, "getID");
            Object functionality = XposedHelpers.callMethod(properties, "isFunctionality");
            return !Boolean.FALSE.equals(functionality)
                    || ControlItemLabels.protectedPopup(id, privateName);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+PopProxy: 无法识别弹窗类型，保留原行为: ", throwable);
            return true;
        }
    }

    public void applyUpdateHooks() {
        if (!PluginSettings.enabled("update")) return;
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
            Method source = cachedTargets.method(HookTargets.KEY_COVER_ORIGINAL_SOURCE_METHOD);
            if (source == null) {
                XposedBridge.log("FQHook+OriginalTitle: 封面原名来源缺失，仅使用已观察到的原名");
            } else if (source.getParameterTypes().length == 1
                    && "com.dragon.read.rpc.model.ApiBookInfo".equals(source.getParameterTypes()[0].getName())
                    && "com.dragon.read.reader.bookcover.BookCoverInfo".equals(source.getReturnType().getName())) {
                XposedBridge.hookMethod(source, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable() || param.getResult() == null) return;
                        try {
                            OriginalBookTitles.original(param.args[0]);
                            param.setResult(OriginalBookTitles.cover(param.getResult()));
                        } catch (Throwable error) {
                            HookUtils.logError("FQHook+OriginalTitle: 封面上游原名处理失败: ", error);
                        }
                    }
                });
                XposedBridge.log("FQHook+OriginalTitle: 已安装封面原名来源: " + source);
            } else {
                XposedBridge.log("FQHook+OriginalTitle: 封面原名来源签名不兼容，跳过: " + source);
            }
        } catch (Throwable error) {
            HookUtils.logError("FQHook+OriginalTitle: 安装封面原名来源失败: ", error);
        }
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
                        try {
                            param.args[0] = OriginalBookTitles.cover(param.args[0]);
                        } catch (Throwable error) {
                            HookUtils.logError("FQHook+OriginalTitle: 封面文字副本处理失败，保留宿主: ", error);
                        }
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

    private void applyKmpChapterControlHooks() {
        installKmpChapterBlock(HookTargets.KEY_KMP_AUTHOR_SAY_GATE_METHOD, true);
        installKmpChapterBlock(HookTargets.KEY_KMP_CHAPTER_CARD_METHOD, false);
        installKmpChapterBlock(HookTargets.KEY_KMP_CHAPTER_FALLBACK_METHOD, false);
    }

    private void installKmpChapterBlock(String key, boolean gate) {
        try {
            Method method = cachedTargets.method(key);
            if (method == null) {
                XposedBridge.log("FQHook+ChapterControl: KMP 章末目标缺失，跳过: " + key);
                return;
            }
            Class<?>[] types = method.getParameterTypes();
            boolean valid = gate
                    ? method.getReturnType() == boolean.class && types.length == 2
                        && "com.dragon.read.rpc.model.GetAuthorSpeakData".equals(types[0].getName())
                        && types[1] == String.class
                    : !method.getReturnType().isPrimitive() && types.length == 1;
            if (!valid || java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                XposedBridge.log("FQHook+ChapterControl: KMP 章末契约不兼容，跳过: " + method);
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                private int logged;

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(gate ? Boolean.FALSE : null);
                    if (logged++ < 3) {
                        XposedBridge.log("FQHook+ChapterControl: 已拦截 KMP 章末: " + key);
                    }
                }
            });
            XposedBridge.log("FQHook+ChapterControl: 已安装 KMP 章末拦截: " + method);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: KMP 章末安装失败 " + key + ": ", throwable);
        }
    }

    public void applyChapterControlHooks() {
        if (PluginSettings.enabled("cover_title")) applyCoverTitleRenderHooks();
        if (PluginSettings.enabled("chapter")) applyKmpChapterControlHooks();

        if (PluginSettings.enabled("chapter")) try {
            Method authorSayMethod = cachedTargets.method(HookTargets.KEY_AUTHOR_SAY_METHOD);
            if (authorSayMethod != null) {
                XposedBridge.hookMethod(authorSayMethod, XC_MethodReplacement.returnConstant(null));
                XposedBridge.log("FQHook+ChapterControl: 已禁用作者说");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用作者说失败: ", throwable);
        }

        if (PluginSettings.enabled("cover_comment")) try {
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

                // 独立缓存可空 Pair 工厂，不再猜测 dispatcher 的混淆方法名。
                Method epub = cachedTargets.method(HookTargets.KEY_COVER_EPUB_COMMENT_METHOD);
                if (epub != null && epub.getDeclaringClass() == coverHotCommentMethod.getDeclaringClass()
                        && epub.getParameterTypes().length == 0
                        && "kotlin.Pair".equals(epub.getReturnType().getName())) {
                    XposedBridge.hookMethod(epub, XC_MethodReplacement.returnConstant(null));
                    XposedBridge.log("FQHook+ChapterControl: 已安装 EPUB 封面热评插入拦截: " + epub);
                } else {
                    XposedBridge.log("FQHook+ChapterControl: EPUB 热评插入入口缺失或契约不兼容，跳过");
                }
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用封面热门评论失败: ", throwable);
        }

        if (PluginSettings.enabled("chapter")) try {
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

        if (PluginSettings.enabled("chapter")) try {
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
