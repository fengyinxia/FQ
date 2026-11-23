package com.fuck.fanqie.hooks.download;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import com.fuck.fanqie.hooks.HookUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Adds a mode choice; never substitutes host privilege decisions or callbacks. */
public final class DownloadModeChooser {
    public interface Listener {
        void onSelected(String bookId, boolean export);
    }

    /** Host confirm dialog; its class and method names stay unobfuscated in 7.4.3.32 and 7.4.5.32. */
    private static final String HOST_CONFIRM_DIALOG_BUILDER = "com.dragon.read.widget.ConfirmDialogBuilder";

    private final DownloadExportModes modes;
    private final Listener listener;
    private final Set<String> pending = Collections.synchronizedSet(new HashSet<String>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Set once the host dialog cannot be built, so later downloads go straight to the system dialog. */
    private boolean hostDialogUnavailable;

    public DownloadModeChooser(DownloadExportModes modes, Listener listener) {
        this.modes = modes;
        this.listener = listener;
    }

    public void install(final Method entry) {
        XposedBridge.hookMethod(entry, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) {
                final String bookId = param.args[0] instanceof String ? (String) param.args[0] : null;
                final Activity activity = findActivity(param.args[1]);
                if (bookId == null || bookId.length() == 0 || activity == null
                        || activity.isFinishing() || activity.isDestroyed()) {
                    XposedBridge.log("FQHook+DownloadMode: 下载入口无有效界面或书籍 ID，保留原下载");
                    return;
                }
                final Object receiver = param.thisObject;
                final Object[] arguments = param.args.clone();
                if (!pending.add(bookId)) {
                    param.setResult(null);
                    cancelHost(arguments);
                    return;
                }
                param.setResult(null);
                Runnable show = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            showChoice(activity, bookId, entry, receiver, arguments);
                        } catch (Throwable throwable) {
                            pending.remove(bookId);
                            HookUtils.logError("FQHook+DownloadMode: 选择弹窗失败，取消本次下载: ", throwable);
                            cancelHost(arguments);
                        }
                    }
                };
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    show.run();
                } else if (!mainHandler.post(show)) {
                    pending.remove(bookId);
                    XposedBridge.log("FQHook+DownloadMode: 无法调度选择弹窗，取消本次下载");
                    cancelHost(arguments);
                }
            }
        });
    }

    private void showChoice(final Activity activity, final String bookId, final Method entry,
                            final Object receiver, final Object[] arguments) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            pending.remove(bookId);
            cancelHost(arguments);
            return;
        }
        Choice choice = new Choice(activity, bookId, entry, receiver, arguments);
        if (hostDialogUnavailable || !showHostChoice(activity, choice)) {
            showSystemChoice(activity, choice);
        }
    }

    /**
     * Shows the host's own confirm dialog so the choice matches the host style. Either button is a
     * selection; dismissing any other way (outside touch, back, close icon) cancels. Dark skin must
     * be opted into: the builder only follows it automatically for skinable activities.
     */
    private boolean showHostChoice(Activity activity, Choice choice) {
        try {
            Class<?> builderClass = XposedHelpers.findClass(HOST_CONFIRM_DIALOG_BUILDER, activity.getClassLoader());
            Object builder = XposedHelpers.newInstance(builderClass, activity);
            XposedHelpers.callMethod(builder, "setTitle", (CharSequence) "下载方式");
            XposedHelpers.callMethod(builder, "setNegativeText", "缓存",
                    (View.OnClickListener) view -> choice.select(false));
            XposedHelpers.callMethod(builder, "setConfirmText", "导出",
                    (View.OnClickListener) view -> choice.select(true));
            XposedHelpers.callMethod(builder, "setSupportDarkSkin", true);
            XposedHelpers.callMethod(builder, "showCloseIcon", true);
            XposedHelpers.callMethod(builder, "setOnDismissListener",
                    (DialogInterface.OnDismissListener) dialog -> choice.onDismiss());
            XposedHelpers.callMethod(builder, "show");
            XposedBridge.log("FQHook+DownloadMode: 已显示宿主样式下载方式选择");
            return true;
        } catch (Throwable throwable) {
            hostDialogUnavailable = true;
            HookUtils.logError("FQHook+DownloadMode: 宿主确认弹窗不可用，改用系统弹窗: ", throwable);
            return false;
        }
    }

    private static void showSystemChoice(Activity activity, Choice choice) {
        new AlertDialog.Builder(activity)
                .setTitle("下载方式")
                .setItems(new String[]{"缓存", "导出"}, (dialog, which) -> choice.select(which == 1))
                .setNegativeButton("取消", null)
                .setOnDismissListener(dialog -> choice.onDismiss())
                .show();
        XposedBridge.log("FQHook+DownloadMode: 已显示下载方式选择");
    }

    /** State of one pending choice; both dialogs dismiss themselves after a selection. */
    private final class Choice {
        private final Activity activity;
        private final String bookId;
        private final Method entry;
        private final Object receiver;
        private final Object[] arguments;
        private final AtomicBoolean selected = new AtomicBoolean();

        Choice(Activity activity, String bookId, Method entry, Object receiver, Object[] arguments) {
            this.activity = activity;
            this.bookId = bookId;
            this.entry = entry;
            this.receiver = receiver;
            this.arguments = arguments;
        }

        void select(boolean export) {
            selected.set(true);
            long request = 0;
            try {
                if (activity.isFinishing() || activity.isDestroyed() || !modes.bind(activity)) {
                    throw new IllegalStateException("下载界面或模式存储不可用");
                }
                request = modes.select(bookId, export);
                listener.onSelected(bookId, export);
                XposedBridge.log("FQHook+DownloadMode: 已选择" + (export ? "导出" : "缓存") + ", bookId=" + bookId);
                // Replay precisely this invocation, with every original argument unchanged.
                XposedBridge.invokeOriginalMethod(entry, receiver, arguments);
            } catch (Throwable throwable) {
                HookUtils.logError("FQHook+DownloadMode: 继续原下载失败: ", throwable);
                if (request != 0) {
                    try {
                        modes.complete(bookId, request);
                    } catch (Throwable cleanup) {
                        HookUtils.logError("FQHook+DownloadMode: 清理失败请求标记失败: ", cleanup);
                    }
                }
                cancelHost(arguments);
                Toast.makeText(activity, "启动下载失败，请重试", Toast.LENGTH_SHORT).show();
            }
        }

        void onDismiss() {
            pending.remove(bookId);
            if (!selected.get()) {
                cancelHost(arguments);
                XposedBridge.log("FQHook+DownloadMode: 已取消下载方式选择");
            }
        }
    }

    private static Activity findActivity(Object context) {
        if (!(context instanceof Context)) {
            return null;
        }
        Context current = (Context) context;
        Set<Context> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            if (!(current instanceof ContextWrapper)) {
                return null;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private static void cancelHost(Object[] arguments) {
        Object callback = arguments[6] == null ? arguments[5] : arguments[6];
        if (callback != null) {
            try {
                // Mirrors the host download dialog's dismissal callback.
                XposedHelpers.callMethod(callback, "invoke", Boolean.FALSE);
            } catch (Throwable throwable) {
                HookUtils.logError("FQHook+DownloadMode: 宿主取消回调失败: ", throwable);
            }
        }
    }
}
