package com.fuck.fanqie.hooks;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.view.View;
import android.widget.ImageView;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XposedBridge;

/** 用宿主布局中的稳定图标资源解析头部 id，不缓存跨版本混淆名。 */
final class MyPageViews {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static int menuId;
    private static int settingsId;

    private MyPageViews() {
    }

    static synchronized View[] findHeader(View root) {
        if (menuId != 0 && settingsId != 0) {
            return checkedHeader(root, menuId, settingsId);
        }
        Resources resources = root.getResources();
        int menuIcon = resources.getIdentifier("skin_mine_icon_side_bar_new_light", "drawable", "com.dragon.read");
        int settingsIcon = resources.getIdentifier("skin_mine_icon_setting_new_light", "drawable", "com.dragon.read");
        if (menuIcon == 0 || settingsIcon == 0) {
            XposedBridge.log("FQHook+MyPageHeader: 稳定头部图标资源缺失，保留原菜单");
            return null;
        }
        Set<Integer> layouts = new HashSet<>();
        Set<String> matches = new HashSet<>();
        int foundMenu = 0, foundSettings = 0;
        try (ZipFile apk = new ZipFile(root.getContext().getApplicationInfo().sourceDir)) {
            Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                String path = entries.nextElement().getName();
                if (!path.startsWith("res/") || !path.endsWith(".xml")) {
                    continue;
                }
                String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
                int layout = resources.getIdentifier(name, "layout", "com.dragon.read");
                if (layout == 0 || !layouts.add(layout)) {
                    continue;
                }
                // 先低成本检查二进制 XML 的资源引用，只解析含双图标的布局。
                try (InputStream input = apk.getInputStream(apk.getEntry(path))) {
                    if (!referencesBoth(input, menuIcon, settingsIcon)) {
                        continue;
                    }
                }
                int menu = 0, settings = 0;
                try (XmlResourceParser parser = resources.getLayout(layout)) {
                    for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                        if (event != XmlPullParser.START_TAG) {
                            continue;
                        }
                        int src = parser.getAttributeResourceValue(ANDROID, "src", 0);
                        int id = parser.getAttributeResourceValue(ANDROID, "id", 0);
                        if (src == menuIcon) {
                            menu = id;
                        } else if (src == settingsIcon) {
                            settings = id;
                        }
                    }
                }
                if (checkedHeader(root, menu, settings) != null) {
                    matches.add(menu + ":" + settings);
                    foundMenu = menu;
                    foundSettings = settings;
                }
            }
            if (matches.size() == 1) {
                menuId = foundMenu;
                settingsId = foundSettings;
                XposedBridge.log("FQHook+MyPageHeader: 按布局图标唯一解析头部按钮");
                return checkedHeader(root, menuId, settingsId);
            }
            XposedBridge.log("FQHook+MyPageHeader: 布局图标候选不唯一，保留原菜单，候选=" + matches.size());
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+MyPageHeader: 布局资源解析失败，保留原菜单: ", throwable);
        }
        return null;
    }

    private static boolean referencesBoth(InputStream input, int first, int second) throws java.io.IOException {
        byte[] buffer = new byte[4096];
        int word = 0, count = 0;
        boolean a = false, b = false;
        for (int size; (size = input.read(buffer)) != -1;) {
            for (int i = 0; i < size; i++) {
                word = (word >>> 8) | ((buffer[i] & 255) << 24);
                if (++count >= 4) {
                    a |= word == first;
                    b |= word == second;
                    if (a && b) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static View[] checkedHeader(View root, int menu, int settings) {
        if (menu == 0 || settings == 0 || menu == settings) {
            return null;
        }
        View a = root.findViewById(menu), b = root.findViewById(settings);
        if (!(a instanceof ImageView) || !(b instanceof ImageView) || a.getParent() != b.getParent()) {
            return null;
        }
        return new View[]{a, b};
    }
}
