package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fuck.fanqie.cache.TargetScanResult;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

/** 缓存失效时仅安装平台生命周期入口，不解析旧目标或中途补装业务Hook。 */
public final class InitialScanHooks {
    public interface Scanner { List<String> scan(TargetScanResult.Progress progress) throws Throwable; }
    private enum State { IDLE, RUNNING, READY, FAILED }
    private final Scanner scanner;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> lines = new ArrayList<>();
    private State state = State.IDLE;
    private WeakReference<Activity> owner = new WeakReference<>(null);
    private AlertDialog dialog;
    private TextView logView;
    private ScrollView scroll;

    public InitialScanHooks(Scanner scanner) { this.scanner = scanner; }

    public void install() {
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                activity.getWindow().getDecorView().post(() -> showSafely(activity));
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.args[0])) showSafely((Activity) param.thisObject);
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (owner.get() == param.thisObject) dismiss();
            }
        });
        XposedBridge.log("FQHook+InitialScan: 已安装首次扫描前台入口");
    }

    private void showSafely(Activity activity) {
        try { show(activity); }
        catch (RuntimeException error) {
            XposedBridge.log("FQHook+InitialScan: 弹窗暂不可用，等待下次前台重试: " + error);
            dismiss();
        }
    }

    private void show(Activity activity) {
        if (!"com.dragon.read".equals(activity.getPackageName()) || activity.isFinishing()
                || activity.isDestroyed() || !activity.hasWindowFocus()) return;
        if (dialog != null && owner.get() == activity && dialog.isShowing()) return;
        dismiss();
        owner = new WeakReference<>(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        logView = new TextView(activity);
        logView.setTextSize(13);
        logView.setLineSpacing(4 * density, 1);
        logView.setPadding((int) (24 * density), (int) (12 * density),
                (int) (24 * density), (int) (12 * density));
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(logView, new ScrollView.LayoutParams(-1, -2));
        // 固定日志视口，日志滚动时弹窗和按钮不跳动。
        int height = (int) Math.min(140 * density, activity.getResources().getDisplayMetrics().heightPixels * 0.24f);
        android.widget.LinearLayout body = new android.widget.LinearLayout(activity);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        body.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, height));
        AlertDialog current = new AlertDialog.Builder(activity)
                .setTitle("反混淆扫描")
                .setView(body)
                .setCancelable(false)
                .setPositiveButton("立即重启", null)
                .setNegativeButton("重试", null).create();
        dialog = current;
        current.setOnShowListener(ignored -> {
            current.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (state == State.READY || state == State.FAILED) PluginSettingsPage.restartHost(activity, view);
            });
            current.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> start());
            update();
            if (state == State.IDLE) start();
        });
        current.show();
        android.view.Window window = current.getWindow();
        if (window != null) {
            android.util.TypedValue color = new android.util.TypedValue();
            current.getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, color, true);
            int background = color.resourceId != 0
                    ? current.getContext().getColor(color.resourceId) : color.data;
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(background);
            shape.setCornerRadius(20 * density);
            window.setBackgroundDrawable(shape);
            window.getDecorView().setClipToOutline(true);
            int width = (int) Math.min(320 * density,
                    activity.getResources().getDisplayMetrics().widthPixels - 48 * density);
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void dismiss() {
        AlertDialog old = dialog;
        dialog = null;
        logView = null;
        scroll = null;
        owner.clear();
        if (old != null && old.isShowing()) old.dismiss();
    }

    private void append(String message) {
        lines.add(message);
        if (lines.size() > 160) lines.remove(0);
        update();
    }

    private void start() {
        if (state == State.RUNNING || state == State.READY) return;
        state = State.RUNNING;
        lines.clear();
        append("开始扫描…");
        Thread worker = new Thread(() -> {
            try {
                List<String> failures = scanner.scan(message -> main.post(() -> append(message)));
                main.post(() -> {
                    lines.clear();
                    if (failures.isEmpty()) lines.add("当前版本已适配");
                    else for (String failure : failures) lines.add("[失败] " + failure);
                    state = State.READY;
                    update();
                });
            } catch (Throwable error) {
                XposedBridge.log("FQHook+InitialScan: 后台扫描失败");
                XposedBridge.log(error);
                main.post(() -> failed(error));
            }
        }, "FQ-target-scan");
        worker.setDaemon(true);
        try { worker.start(); }
        catch (Throwable error) { failed(error); }
    }

    private void failed(Throwable error) {
        lines.clear();
        lines.add("[失败] 扫描未完成：" + error.getClass().getSimpleName());
        state = State.FAILED;
        update();
    }

    private void update() {
        Activity activity = owner.get();
        if (dialog == null || logView == null || activity == null
                || activity.isFinishing() || activity.isDestroyed()) return;
        Button restart = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button retry = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (restart == null || retry == null) return;
        // 扫描期间按钮始终可见但禁用，完成（含部分失败）后即可重启。
        restart.setEnabled(state == State.READY || state == State.FAILED);
        retry.setVisibility(state == State.FAILED ? View.VISIBLE : View.GONE);
        logView.setText(android.text.TextUtils.join("\n", lines));
        ScrollView current = scroll;
        if (state == State.RUNNING) current.post(() -> current.fullScroll(View.FOCUS_DOWN));
        else current.post(() -> current.scrollTo(0, 0));
    }
}
