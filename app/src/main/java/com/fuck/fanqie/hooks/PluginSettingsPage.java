package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

import com.fuck.fanqie.BuildConfig;
import com.fuck.fanqie.PluginSettings;
import com.fuck.fanqie.DynamicItems;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** 宿主内单页设置；多选使用选择框，保存配置与运行配置分离。 */
final class PluginSettingsPage {
    private static final String EXTRA_PAGE = "com.fuck.fanqie.extra.PLUGIN_SETTINGS_PAGE";
    private static Method nightMode;
    private static boolean lifecycleInstalled;
    private static final Map<Activity, Boolean> pageThemes = Collections.synchronizedMap(new java.util.WeakHashMap<Activity, Boolean>());

    private PluginSettingsPage() {
    }

    static boolean install(Method create, Method night, Method change) {
        nightMode = night;
        if (change != null) {
            XposedBridge.hookMethod(change, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    Activity[] pages;
                    synchronized (pageThemes) { pages = pageThemes.keySet().toArray(new Activity[0]); }
                    for (Activity page : pages) page.runOnUiThread(() -> refreshTheme(page));
                }
            });
        }
        if (create == null || Modifier.isStatic(create.getModifiers())
                || !Activity.class.isAssignableFrom(create.getDeclaringClass())
                || create.getReturnType() != void.class
                || create.getParameterTypes().length != 1
                || create.getParameterTypes()[0] != android.os.Bundle.class) {
            XposedBridge.log("FQHook+Settings: 页面承载目标缺失或不兼容，跳过入口");
            return false;
        }
        XposedBridge.hookMethod(create, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) {
                    return;
                }
                Activity activity = (Activity) param.thisObject;
                if (!isPluginPage(activity) || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                // 保留原 onCreate 初始化及生命周期，避免跳过 super 或留下未初始化宿主字段。
                // 只替换带独立标记的新实例；原设置列表仍保留在任务栈下一层。
                try {
                    installLifecycle(activity.getApplication());
                    render(activity);
                } catch (Throwable throwable) {
                    HookUtils.logError("FQHook+Settings: 设置页面创建失败: ", throwable);
                    activity.finish();
                }
            }
        });
        XposedBridge.log("FQHook+Settings: 已安装独立页面承载: " + create);
        return true;
    }

    static void open(Activity activity, Method create) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> open(activity, create));
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed() || isPluginPage(activity)
                || !activity.hasWindowFocus()) {
            return;
        }
        try {
            Intent intent = new Intent(activity, create.getDeclaringClass());
            intent.putExtra(EXTRA_PAGE, true);
            activity.startActivity(intent);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Settings: 打开设置页面失败: ", throwable);
        }
    }

    private static boolean isPluginPage(Activity activity) {
        Intent intent = activity.getIntent();
        return intent != null && intent.getBooleanExtra(EXTRA_PAGE, false);
    }

    private static boolean isDark(Activity activity) {
        if (nightMode != null) {
            try { return Boolean.TRUE.equals(nightMode.invoke(null)); }
            catch (ReflectiveOperationException error) { HookUtils.logError("FQHook+Settings: 宿主主题读取失败: ", error); }
        }
        return (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static void installLifecycle(android.app.Application application) {
        if (lifecycleInstalled) return;
        lifecycleInstalled = true;
        application.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) { refreshTheme(activity); }
            @Override public void onActivityDestroyed(Activity activity) { pageThemes.remove(activity); }
            @Override public void onActivityCreated(Activity activity, android.os.Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, android.os.Bundle state) { }
        });
    }

    private static void refreshTheme(Activity activity) {
        Boolean old = pageThemes.get(activity);
        if (old == null || !isPluginPage(activity) || activity.isFinishing() || activity.isDestroyed()
                || old == isDark(activity)) return;
        try {
            View content = activity.findViewById(android.R.id.content);
            ScrollView scroll = findScroll(content);
            int position = scroll == null ? 0 : scroll.getScrollY();
            render(activity);
            ScrollView next = findScroll(activity.findViewById(android.R.id.content));
            if (next != null) next.post(() -> next.scrollTo(0, position));
        } catch (Throwable error) { HookUtils.logError("FQHook+Settings: 主题刷新失败: ", error); }
    }

    private static ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView result = findScroll(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void render(Activity activity) {
        boolean dark = isDark(activity);
        // 只调整带模块标记的独立SettingsActivity，不改宿主全局Theme或夜间配置。
        // 原生弹窗、Switch及底栏编辑器也从此Activity继承一致主题。
        activity.setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        pageThemes.put(activity, dark);
        XposedBridge.log("FQHook+Settings: 设置页主题=" + (dark ? "dark" : "light"));
        int background = themeColor(activity, android.R.attr.colorBackground,
                dark ? Color.rgb(24, 24, 24) : Color.rgb(250, 250, 250));
        int primary = themeColor(activity, android.R.attr.textColorPrimary, dark ? Color.WHITE : Color.BLACK);
        int secondary = themeColor(activity, android.R.attr.textColorSecondary,
                dark ? Color.LTGRAY : Color.DKGRAY);

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(background);
        page.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        // 使用原 Activity 的内容区与系统栏适配，不覆盖 decor 或添加独立 Window。
        page.setFitsSystemWindows(true);

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text(activity, "‹", 32, primary);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回番茄设置");
        back.setOnClickListener(view -> activity.finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(activity, 56), -1));
        TextView title = text(activity, "FQ 插件设置", 20, primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView restart = text(activity, "重启", 15, primary);
        restart.setGravity(Gravity.CENTER);
        restart.setContentDescription("重启宿主");
        restart.setFocusable(true);
        restart.setOnClickListener(view -> new AlertDialog.Builder(activity).setTitle("重启番茄？")
                .setMessage("将关闭当前页面并应用已保存设置。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重启", (dialog, which) -> restartHost(activity, restart)).show());
        toolbar.addView(restart, new LinearLayout.LayoutParams(dp(activity, 64), -1));
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(activity, 56)));

        View divider = new View(activity);
        divider.setBackgroundColor(dark ? 0x33FFFFFF : 0x18000000);
        page.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 24));
        TextView status = text(activity, "", 13, secondary);
        status.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
        body.addView(status, new LinearLayout.LayoutParams(-1, -2));
        updateStatus(status);
        for (String group : new String[]{"内容屏蔽", "UI 净化", "拓展"}) {
            addText(activity, body, group, 17, primary);
            LinearLayout card = card(activity, body, dark);
            for (PluginSettings.Option option : PluginSettings.OPTIONS) {
                if (group.equals(option.group)) setting(activity, card, option, status, primary, secondary, dark);
            }
        }
        addText(activity, body, "FQ " + BuildConfig.VERSION_NAME, 13, secondary);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        activity.setContentView(page);
        activity.setTitle("FQ 插件设置");
        XposedBridge.log("FQHook+Settings: 已打开功能设置页面");
    }

    static void restartHost(Activity activity, View button) {
        if (!button.isEnabled()) return;
        android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
        boolean[] pending = {true};
        Runnable timeout = () -> {
            if (!pending[0]) return;
            pending[0] = false;
            button.setEnabled(true);
            if (!activity.isDestroyed() && !activity.isFinishing() && activity.hasWindowFocus()) {
                Toast.makeText(activity, "重启未完成，请重试", Toast.LENGTH_SHORT).show();
            }
        };
        try {
            if (!DynamicItems.flushPending()) {
                XposedBridge.log("FQHook+Settings: 目录信息未保存，继续应用已保存设置");
                Toast.makeText(activity, "目录信息未保存，继续应用已保存设置", Toast.LENGTH_SHORT).show();
            }
            long deadline = android.os.SystemClock.elapsedRealtime()
                    + com.fuck.fanqie.RestartHostActivity.REQUEST_TIMEOUT_MS;
            android.os.ResultReceiver ready = new android.os.ResultReceiver(handler) {
                @Override protected void onReceiveResult(int result, android.os.Bundle data) {
                    if (result != 1 || !pending[0]) return;
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) { timeout.run(); return; }
                    pending[0] = false;
                    handler.removeCallbacks(timeout);
                    XposedBridge.log("FQHook+Settings: 重启承接页就绪，退出宿主主进程");
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            };
            Intent intent = new Intent().setClassName("com.fuck.fanqie",
                    "com.fuck.fanqie.RestartHostActivity");
            intent.putExtra("restart_ready", ready);
            android.os.Bundle handshake = new android.os.Bundle();
            handshake.putBinder("host_lifetime", new android.os.Binder());
            handshake.putLong("deadline", deadline);
            intent.putExtra("restart_handshake", handshake);
            button.setEnabled(false);
            activity.startActivityForResult(intent, 0x4651);
            handler.postDelayed(timeout, Math.max(0, deadline - android.os.SystemClock.elapsedRealtime()));
        } catch (Throwable throwable) {
            pending[0] = false;
            handler.removeCallbacks(timeout);
            button.setEnabled(true);
            HookUtils.logError("FQHook+Settings: 重启失败，保留宿主进程: ", throwable);
            Toast.makeText(activity, "重启失败，请手动重启番茄", Toast.LENGTH_SHORT).show();
        }
    }

    private static LinearLayout card(Activity activity, LinearLayout body, boolean dark) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 16), dp(activity, 4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0x12FFFFFF : 0x08000000);
        background.setCornerRadius(dp(activity, 12));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 12);
        body.addView(card, params);
        return card;
    }

    private static void updateStatus(TextView status) {
        status.setText(PluginSettings.pending() ? "已保存，重启番茄后生效" : "修改后重启番茄生效");
    }

    private static void setting(Activity activity, LinearLayout card, PluginSettings.Option option,
                                TextView status, int primary, int secondary, boolean dark) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(activity, 56));
        TextView label = text(activity, option.title, 15, primary);
        label.setPadding(0, dp(activity, 12), dp(activity, 12), dp(activity, 12));
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Map<String, Boolean> saved = PluginSettings.saved();
        if (option.navigation()) {
            TextView summary = text(activity, NavigationEditor.summary(), 13, secondary);
            summary.setTag("fq_nav_" + option.key);
            row.addView(summary, new LinearLayout.LayoutParams(-2, -2));
            row.setFocusable(true);
            row.setContentDescription(option.title);
            row.setOnClickListener(view -> NavigationEditor.open(activity, () -> {
                summary.setText(NavigationEditor.summary());
                updateStatus(status);
            }));
        } else if (option.dynamic()) {
            TextView summary = text(activity, dynamicSummary(option.key), 13, secondary);
            row.addView(summary, new LinearLayout.LayoutParams(-2, -2));
            row.setFocusable(true);
            row.setContentDescription(option.title);
            row.setOnClickListener(view -> showDynamic(activity, option, summary, status));
        } else if (option.multiple()) {
            TextView summary = text(activity, summary(option, saved), 13, secondary);
            row.addView(summary, new LinearLayout.LayoutParams(-2, -2));
            row.setFocusable(true);
            row.setContentDescription(option.title);
            row.setOnClickListener(view -> {
                Map<String, Boolean> current = PluginSettings.saved();
                boolean[] selected = new boolean[option.keys.length];
                for (int i = 0; i < selected.length; i++) selected[i] = current.get(option.keys[i]);
                new AlertDialog.Builder(activity).setTitle(option.title)
                        .setMultiChoiceItems(option.labels, selected, (dialog, which, checked) -> selected[which] = checked)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("保存", (dialog, which) -> {
                            Map<String, Boolean> changes = new HashMap<>();
                            for (int i = 0; i < selected.length; i++) changes.put(option.keys[i], selected[i]);
                            if (save(activity, changes)) {
                                summary.setText(summary(option, PluginSettings.saved()));
                                updateStatus(status);
                            }
                        }).show();
            });
        } else {
            Switch control = new Switch(activity);
            control.setContentDescription(option.title);
            control.setChecked(saved.get(option.key));
            final boolean[] reverting = {false};
            control.setOnCheckedChangeListener((button, checked) -> {
                if (reverting[0]) return;
                if (!save(activity, Collections.singletonMap(option.key, checked))) {
                    reverting[0] = true;
                    button.setChecked(!checked);
                    reverting[0] = false;
                }
                updateStatus(status);
            });
            row.addView(control, new LinearLayout.LayoutParams(-2, -2));
        }
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(activity);
        divider.setBackgroundColor(dark ? 0x18FFFFFF : 0x10000000);
        card.addView(divider, new LinearLayout.LayoutParams(-1, dp(activity, 1)));
    }

    private static String dynamicSummary(String area) {
        List<DynamicItems.Item> items = DynamicItems.list(area);
        int selected = 0;
        for (DynamicItems.Item item : items) if (DynamicItems.selected(item)) selected++;
        return items.isEmpty() ? "暂未发现  ›" : selected + "/" + items.size() + "  ›";
    }

    private static void showDynamic(Activity activity, PluginSettings.Option option, TextView summary, TextView status) {
        List<DynamicItems.Item> items = DynamicItems.list(option.key);
        if (items.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle(option.title)
                    .setMessage("请浏览" + ("mine".equals(option.key) ? "我的页" : DynamicItems.SEARCH.equals(option.key) ? "搜索结果页" : "top_tabs".equals(option.key) ? "书城顶部频道" : "书城推荐") + "后重试")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        String[] labels = new String[items.size()];
        boolean[] selected = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i).display();
            selected[i] = DynamicItems.selected(items.get(i));
        }
        new AlertDialog.Builder(activity).setTitle(option.title + " · 勾选屏蔽")
                .setMultiChoiceItems(labels, selected, (dialog, which, checked) -> selected[which] = checked)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        Set<String> blocked = new HashSet<>();
                        for (int i = 0; i < items.size(); i++) if (selected[i]) blocked.add(items.get(i).id);
                        DynamicItems.save(option.key, items, blocked);
                        summary.setText(dynamicSummary(option.key));
                        updateStatus(status);
                        XposedBridge.log("FQHook+Dynamic: 已保存 " + option.key + " 屏蔽选择，数量=" + blocked.size());
                    } catch (Throwable throwable) {
                        HookUtils.logError("FQHook+Dynamic: 选择保存失败: ", throwable);
                        Toast.makeText(activity, "保存失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private static String summary(PluginSettings.Option option, Map<String, Boolean> values) {
        int count = 0;
        for (String key : option.keys) if (values.get(key)) count++;
        return count + "/" + option.keys.length + "  ›";
    }

    private static boolean save(Activity activity, Map<String, Boolean> changes) {
        try {
            PluginSettings.save(changes);
            XposedBridge.log("FQHook+Settings: 已保存配置，待重启生效: " + changes);
            return true;
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Settings: 保存配置失败: ", throwable);
            Toast.makeText(activity, "保存失败，请重试", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static TextView text(Activity activity, String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private static void addText(Activity activity, LinearLayout body, String value, int size, int color) {
        TextView text = text(activity, value, size, color);
        text.setPadding(0, dp(activity, 8), 0, dp(activity, 12));
        text.setLineSpacing(dp(activity, 3), 1);
        body.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }

    private static int themeColor(Activity activity, int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (activity.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                return activity.getResources().getColorStateList(value.resourceId, activity.getTheme()).getDefaultColor();
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return fallback;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
