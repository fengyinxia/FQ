package com.fuck.fanqie.hooks;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 只解析当前我的页实际 inflate 的布局，不在主线程遍历 APK。 */
final class MyPageViews {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final ThreadLocal<Map<View, Integer>> inflating = new ThreadLocal<>();
    // 只缓存布局内资源 ID（包括失败），不持有页面或 Activity。
    private static final Map<Integer, int[]> headers = new HashMap<>();
    private static boolean tracking;

    private MyPageViews() { }

    static synchronized void trackLayouts() {
        if (tracking) return;
        XposedHelpers.findAndHookMethod(LayoutInflater.class, "inflate", int.class,
                ViewGroup.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Map<View, Integer> active = inflating.get();
                        if (active != null && param.getResult() instanceof View) {
                            active.put((View) param.getResult(), (Integer) param.args[0]);
                        }
                    }
                });
        tracking = true;
    }

    static void beginContent() {
        inflating.set(new IdentityHashMap<View, Integer>());
    }

    static Integer finishContent(Object root) {
        Map<View, Integer> active = inflating.get();
        inflating.remove();
        return active == null ? null : active.get(root);
    }

    static synchronized View[] findHeader(View root, Integer layout) {
        if (layout == null) {
            XposedBridge.log("FQHook+MyPageHeader: 未捕获当前页面布局，保留原菜单");
            return null;
        }
        int[] ids = headers.get(layout);
        if (ids != null) return checkedHeader(root, ids[0], ids[1]);
        int menu = 0, settings = 0;
        Resources resources = root.getResources();
        int menuIcon = resources.getIdentifier("skin_mine_icon_side_bar_new_light", "drawable", "com.dragon.read");
        int settingsIcon = resources.getIdentifier("skin_mine_icon_setting_new_light", "drawable", "com.dragon.read");
        try {
            if (menuIcon != 0 && settingsIcon != 0) {
                try (XmlResourceParser parser = resources.getLayout(layout)) {
                    for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                        if (event != XmlPullParser.START_TAG) continue;
                        int src = parser.getAttributeResourceValue(ANDROID, "src", 0);
                        int id = parser.getAttributeResourceValue(ANDROID, "id", 0);
                        if (src == menuIcon) {
                            if (menu != 0 && menu != id) throw new IllegalStateException("菜单图标不唯一");
                            menu = id;
                        } else if (src == settingsIcon) {
                            if (settings != 0 && settings != id) throw new IllegalStateException("设置图标不唯一");
                            settings = id;
                        }
                    }
                }
            }
            if (checkedHeader(root, menu, settings) != null) {
                headers.put(layout, new int[]{menu, settings});
                XposedBridge.log("FQHook+MyPageHeader: 仅解析当前页面布局，头部按钮匹配成功");
                return checkedHeader(root, menu, settings);
            }
            XposedBridge.log("FQHook+MyPageHeader: 当前页面布局未唯一匹配头部，保留原菜单");
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageHeader: 当前布局解析失败，保留原菜单: ", throwable);
        }
        headers.put(layout, new int[]{0, 0});
        return null;
    }

    private static View[] checkedHeader(View root, int menu, int settings) {
        if (menu == 0 || settings == 0 || menu == settings) return null;
        View a = root.findViewById(menu), b = root.findViewById(settings);
        if (!(a instanceof ImageView) || !(b instanceof ImageView) || a.getParent() != b.getParent()) return null;
        return new View[]{a, b};
    }
}
