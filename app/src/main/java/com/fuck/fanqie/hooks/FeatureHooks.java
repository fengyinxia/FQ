package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

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
            Class<?> runnableClass = XposedHelpers.findClass("com.dragon.read.pop.IPopProxy$IRunnable", hostClassLoader);
            Class<?> listenerClass = XposedHelpers.findClass("com.dragon.read.pop.IPopProxy$IListener", hostClassLoader);
            Class<?> silkRoadClass = XposedHelpers.findClass("com.bytedance.component.silk.road.subwindow.b", hostClassLoader);
            hookPopProxyMethod(popProxyClass, "enqueue", propertiesClass, runnableClass, listenerClass);
            hookPopProxyMethod(popProxyClass, "popup", propertiesClass, runnableClass, listenerClass);
            hookPopProxyMethod(popProxyClass, "popup", propertiesClass, silkRoadClass, listenerClass);
            XposedBridge.log("FQHook+applyPopProxyHooks: 已启用 PopProxy 条件拦截 (privacy_dialog 放行)");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+applyPopProxyHooks: Hook PopProxy失败: ", throwable);
        }
    }

    private void hookPopProxyMethod(Class<?> popProxyClass, final String methodName,
                                    Class<?> propertiesClass, Class<?> actionClass,
                                    Class<?> listenerClass) {
        XposedHelpers.findAndHookMethod(
                popProxyClass,
                methodName,
                Activity.class, propertiesClass, actionClass, listenerClass, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String hookedSignature = param.method == null ? "null" : param.method.toString();
                        Activity activity = param.args[0] instanceof Activity ? (Activity) param.args[0] : null;
                        Object properties = param.args[1];
                        Object action = param.args[2];
                        Object listener = param.args[3];
                        Object scene = param.args[4];
                        boolean allow = shouldAllowPopup(properties);
                        XposedBridge.log("FQHook+PopProxy: [LOG] " + methodName
                                + ", hookedSignature=" + hookedSignature
                                + ", expectedActionType=" + actionClass.getName()
                                + ", decision=" + (allow ? "allow" : "block")
                                + ", activity=" + (activity == null ? "null" : activity.getClass().getName())
                                + ", properties=" + describeObject(properties)
                                + ", action=" + (action == null ? "null" : action.getClass().getName())
                                + ", listener=" + (listener == null ? "null" : listener.getClass().getName())
                                + ", scene=" + String.valueOf(scene));
                        if (!allow) {
                            param.setResult(null);
                        }
                    }
                }
        );
    }

    private boolean shouldAllowPopup(Object properties) {
        if (properties == null) {
            return false;
        }
        String className = properties.getClass().getName();
        return className.contains("privacy_dialog");
    }

    private String describeObject(Object target) {
        if (target == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(target.getClass().getName());
        int count = 0;
        for (java.lang.reflect.Field field : target.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            builder.append(count == 0 ? "{" : ", ");
            if (count == 6) {
                builder.append("...");
                count++;
                break;
            }
            builder.append(field.getName()).append('=');
            try {
                field.setAccessible(true);
                Object value = field.get(target);
                if (value == null || value instanceof CharSequence || value instanceof Number
                        || value instanceof Boolean || value instanceof Character
                        || value.getClass().isEnum()) {
                    builder.append(String.valueOf(value));
                } else {
                    builder.append('<').append(value.getClass().getName()).append('>');
                }
            } catch (Throwable throwable) {
                builder.append("<error>");
            }
            count++;
        }
        if (count > 0) {
            builder.append('}');
        }
        return builder.toString();
    }

    public void applyUpdateHooks() {
        Method updateMethod = cachedTargets.method(HookTargets.KEY_UPDATE_METHOD);
        if (updateMethod == null) {
            XposedBridge.log("FQHook+HookApplier: 未找到更新消息处理方法(B0)，跳过Hook");
        } else {
            XposedBridge.hookMethod(updateMethod, XC_MethodReplacement.returnConstant(null));
            XposedBridge.log("FQHook+HookApplier: 已应用更新消息拦截Hook(B0)");
        }

        Method checkUpdateMethod = cachedTargets.method(HookTargets.KEY_CHECK_UPDATE_METHOD);
        if (checkUpdateMethod == null) {
            XposedBridge.log("FQHook+HookApplier: 未找到检查更新方法(H0)，跳过Hook");
        } else {
            XposedBridge.hookMethod(checkUpdateMethod, XC_MethodReplacement.returnConstant(Boolean.FALSE));
            XposedBridge.log("FQHook+HookApplier: 已应用检查更新拦截Hook(H0)");
        }
    }

    public void applyChapterControlHooks() {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.dragon.read.reader.bookcover.BookCoverInfo",
                    hostClassLoader,
                    "getBookShortName",
                    XC_MethodReplacement.returnConstant(null)
            );
            XposedBridge.log("FQHook+ChapterControl: 成功Hook getBookShortName");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: Hook getBookShortName失败: ", throwable);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "com.dragon.read.reader.bookcover.BookCoverInfo",
                    hostClassLoader,
                    "getBookNameUrl",
                    XC_MethodReplacement.returnConstant(null)
            );
            XposedBridge.log("FQHook+ChapterControl: 成功Hook getBookNameUrl");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: Hook getBookNameUrl失败: ", throwable);
        }

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
            } else {
                XposedBridge.hookMethod(coverHotCommentMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            java.lang.reflect.Field field = XposedHelpers.findField(param.thisObject.getClass(), "c");
                            Class<?> type = field.getType();
                            if (!type.isPrimitive()) {
                                field.set(param.thisObject, null);
                            } else if (type == boolean.class) {
                                field.setBoolean(param.thisObject, false);
                            } else if (type == byte.class) {
                                field.setByte(param.thisObject, (byte) 0);
                            } else if (type == short.class) {
                                field.setShort(param.thisObject, (short) 0);
                            } else if (type == int.class) {
                                field.setInt(param.thisObject, 0);
                            } else if (type == long.class) {
                                field.setLong(param.thisObject, 0L);
                            } else if (type == float.class) {
                                field.setFloat(param.thisObject, 0F);
                            } else if (type == double.class) {
                                field.setDouble(param.thisObject, 0D);
                            } else if (type == char.class) {
                                field.setChar(param.thisObject, '\0');
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
                XposedBridge.log("FQHook+ChapterControl: 已禁用封面热门评论");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用封面热门评论失败: ", throwable);
        }

        try {
            Method chapterEndHotCommentMethod = cachedTargets.method(HookTargets.KEY_CHAPTER_END_HOT_COMMENT_METHOD);
            if (chapterEndHotCommentMethod != null) {
                XposedBridge.hookMethod(chapterEndHotCommentMethod, XC_MethodReplacement.returnConstant(Boolean.FALSE));
                XposedBridge.log("FQHook+ChapterControl: 已禁用章末热评");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用章末热评失败: ", throwable);
        }

        try {
            Method chapterEndControlMethod = cachedTargets.method(HookTargets.KEY_CHAPTER_END_CONTROL_METHOD);
            if (chapterEndControlMethod == null) {
                XposedBridge.log("FQHook+ChapterControl: 未找到章末控件方法，跳过Hook");
            } else {
                XposedBridge.hookMethod(chapterEndControlMethod, XC_MethodReplacement.returnConstant(null));
                XposedBridge.log("FQHook+ChapterControl: 已禁用章末评论开关和礼物控件");
            }
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+ChapterControl: 禁用章末控件失败: ", throwable);
        }
    }
}
