package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.fuck.fanqie.NavigationSettings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 显示、拖拽顺序、主页同框编辑，保存前只修改草稿。 */
final class NavigationEditor {
    static String summary() {
        NavigationSettings.Layout layout = NavigationSettings.saved();
        return layout.order.isEmpty() ? "暂未发现  ›"
                : NavigationSettings.visible(layout.order, layout.hidden).size() + "/" + layout.order.size() + "  ›";
    }
    static void open(Activity activity, Runnable changed) {
        NavigationSettings.Layout layout = NavigationSettings.saved();
        if (layout.order.isEmpty()) {
            Toast.makeText(activity, "请返回主界面后重试", Toast.LENGTH_SHORT).show(); return;
        }
        Draft draft = new Draft(activity, layout);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("底栏自定义 · 长按拖动排序")
                .setView(draft.scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnDismissListener(d -> draft.stopScrolling());
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                NavigationSettings.save(new NavigationSettings.Layout(draft.order, draft.hidden, draft.home));
                changed.run(); dialog.dismiss();
            } catch (Throwable error) {
                if (!(error instanceof IllegalArgumentException)) HookUtils.logError("FQHook+Navigation: 保存未完成: ", error);
                Toast.makeText(activity, error instanceof IllegalArgumentException ? error.getMessage() : "保存失败，请重试", Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private static final class Draft {
        final Activity activity;
        final List<String> order;
        final Set<String> hidden, available;
        final LinearLayout body;
        final ScrollView scroll;
        String home, dragging;
        View draggedView;
        List<String> beforeDrag;
        float pointerY;
        boolean dropped;
        final Runnable autoScroll = new Runnable() {
            @Override public void run() {
                if (dragging == null) return;
                int edge = dp(activity, 48);
                int delta = pointerY < edge ? -dp(activity, 10)
                        : pointerY > scroll.getHeight() - edge ? dp(activity, 10) : 0;
                if (delta != 0) {
                    scroll.scrollBy(0, delta);
                    moveToPointer();
                }
                scroll.postDelayed(this, 32);
            }
        };
        Draft(Activity activity, NavigationSettings.Layout layout) {
            this.activity = activity;
            order = new ArrayList<>(layout.order);
            hidden = new HashSet<>(layout.hidden);
            available = NavigationSettings.available();
            home = layout.home;
            body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            scroll = new ScrollView(activity);
            scroll.addView(body);
            // 只接收本编辑器的本地拖拽，不读取外部ClipData。
            scroll.setOnDragListener((view, event) -> drag(event));
            render();
        }
        void render() {
            body.removeAllViews();
            for (String id : order) {
                String title = NavigationSettings.label(id);
                boolean supported = available.contains(id);
                LinearLayout row = new LinearLayout(activity);
                row.setTag(id);
                row.setGravity(Gravity.CENTER_VERTICAL);
                CheckBox visible = new CheckBox(activity);
                visible.setText(title + (supported ? "" : "（暂不可用）"));
                visible.setTextSize(14);
                visible.setContentDescription("显示" + title);
                visible.setChecked(!hidden.contains(id));
                visible.setEnabled(supported);
                visible.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) hidden.remove(id); else hidden.add(id);
                    render();
                });
                row.addView(visible, new LinearLayout.LayoutParams(0, dp(activity, 52), 1));
                RadioButton main = new RadioButton(activity);
                main.setText("主页"); main.setTextSize(12);
                main.setContentDescription("主页选" + title);
                main.setChecked(id.equals(home));
                main.setEnabled(supported && !hidden.contains(id));
                main.setOnClickListener(v -> { home = id; render(); });
                row.addView(main, new LinearLayout.LayoutParams(dp(activity, 78), dp(activity, 52)));
                TextView handle = new TextView(activity);
                handle.setText("≡"); handle.setTextSize(26); handle.setGravity(Gravity.CENTER);
                handle.setContentDescription("拖动" + title + "排序");
                row.addView(handle, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 52)));
                View.OnLongClickListener start = v -> startDrag(row, id);
                row.setOnLongClickListener(start);
                handle.setOnLongClickListener(start);
                visible.setOnLongClickListener(start);
                main.setOnLongClickListener(start);
                body.addView(row);
            }
        }
        boolean startDrag(View row, String id) {
            if (dragging != null) return false;
            beforeDrag = new ArrayList<>(order);
            dragging = id;
            draggedView = row;
            dropped = false;
            ClipData data = ClipData.newPlainText("fq_navigation", "");
            boolean started = row.startDragAndDrop(data, new View.DragShadowBuilder(row), this, 0);
            if (!started) { dragging = null; draggedView = null; return false; }
            row.setAlpha(0.3f);
            return true;
        }
        boolean drag(DragEvent event) {
            if (event.getLocalState() != this) return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    pointerY = event.getY();
                    moveToPointer();
                    stopScrolling();
                    scroll.postDelayed(autoScroll, 32);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    stopScrolling();
                    return true;
                case DragEvent.ACTION_DROP:
                    pointerY = event.getY();
                    moveToPointer();
                    dropped = true;
                    stopScrolling();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    stopScrolling();
                    if (!dropped) { order.clear(); order.addAll(beforeDrag); }
                    dragging = null;
                    draggedView = null;
                    render();
                    return true;
                default: return true;
            }
        }
        void moveToPointer() {
            if (dragging == null || draggedView == null) return;
            float y = pointerY + scroll.getScrollY();
            int insertion = 0;
            for (int i = 0; i < body.getChildCount(); i++) {
                View child = body.getChildAt(i);
                if (child == draggedView) continue;
                if (y > (child.getTop() + child.getBottom()) / 2f) insertion++;
            }
            int old = order.indexOf(dragging);
            if (old == insertion) return;
            order.remove(old);
            order.add(insertion, dragging);
            body.removeView(draggedView);
            body.addView(draggedView, insertion);
        }
        void stopScrolling() { scroll.removeCallbacks(autoScroll); }
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
