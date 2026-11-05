package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.NavigationSettings;
import com.fuck.fanqie.cache.CachedTargets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 路由列表统一过滤/排序；主页仅在首次正常启动构建期间生效。 */
public final class BottomTabHooks extends BaseHook {
    private static final String LAUNCHER_ORIGIN = "com.fuck.fanqie.extra.LAUNCHER_ORIGIN";
    private final CachedTargets targets;
    private boolean categorySupported, refreshInstalled, firstMain = true;
    private List<String> effective = Collections.emptyList();
    private final Set<Object> launching = Collections.newSetFromMap(new WeakHashMap<>());
    public BottomTabHooks(CachedTargets targets, ClassLoader loader) { super(loader); this.targets = targets; }

    @Override public void apply() {
        try {
            Class<?> route = targets.type(HookTargets.KEY_TAB_ROUTE_HELPER_CLASS);
            Method build = targets.method(HookTargets.KEY_TAB_METHOD);
            if (route == null || build == null) {
                XposedBridge.log("FQHook+Navigation: 路由/构建契约缺失，保留宿主底栏");
                return;
            }
            XposedBridge.hookMethod(build, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) { normalize(route); }
            });
            installRefresh(route);
            installCategoryGate();
            installHome(route);
        } catch (Throwable error) { HookUtils.logError("FQHook+Navigation: 安装失败: ", error); }
    }
    private void installHome(Class<?> route) {
        try {
            Method create = targets.method(HookTargets.KEY_MAIN_ACTIVITY_ON_CREATE_METHOD);
            Method home = targets.method(HookTargets.KEY_MAIN_HOME_TARGET_METHOD);
            if (create == null || home == null || !home.getReturnType().isEnum()) {
                XposedBridge.log("FQHook+Navigation: 主页契约缺失或不兼容，仅跳过主页覆盖");
                return;
            }
            installLauncherOrigin();
            XposedBridge.hookMethod(create, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    normalize(route);
                    boolean initial = firstMain;
                    firstMain = false;
                    if (initial && param.args[0] == null && normalLaunch(((Activity) param.thisObject).getIntent())) {
                        launching.add(param.thisObject);
                    }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) { launching.remove(param.thisObject); }
            });
            XposedBridge.hookMethod(home, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || !launching.contains(param.thisObject)) return;
                    String id = NavigationSettings.runtimeHome(effective);
                    if (id == null) return;
                    for (Object value : home.getReturnType().getEnumConstants()) {
                        if (((Enum<?>) value).name().equals(id)) {
                            param.setResult(value);
                            XposedBridge.log("FQHook+Navigation: 启动主页=" + id);
                            return;
                        }
                    }
                }
            });
        } catch (Throwable error) { HookUtils.logError("FQHook+Navigation: 主页安装失败，保留底栏显示与排序: ", error); }
    }
    private static boolean launcherIntent(Intent intent) {
        return intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.getData() == null;
    }
    private void installLauncherOrigin() {
        Method splash = targets.method(HookTargets.KEY_SPLASH_K1_METHOD);
        if (splash == null) return;
        XposedBridge.hookMethod(splash, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity) || param.args.length == 0
                        || !(param.args[0] instanceof Intent)) return;
                Intent origin = ((Activity) param.thisObject).getIntent();
                Intent outgoing = (Intent) param.args[0];
                Uri data = outgoing.getData();
                String requested = origin == null ? null : origin.getStringExtra("tabName");
                boolean neutralOrigin = requested == null || requested.isEmpty() || "default".equals(requested);
                if (launcherIntent(origin) && neutralOrigin && data != null
                        && "dragon1967".equals(data.getScheme()) && "main".equals(data.getHost())) {
                    outgoing.putExtra(LAUNCHER_ORIGIN, true);
                }
            }
        });
    }
    private static boolean normalLaunch(Intent intent) {
        if (intent == null) return false;
        boolean fromLauncher = intent.getBooleanExtra(LAUNCHER_ORIGIN, false);
        intent.removeExtra(LAUNCHER_ORIGIN);
        if (fromLauncher) return true; // 来源由Splash原始MAIN/LAUNCHER确认，不根据seriesmall猜测。
        if (!launcherIntent(intent)) return false;
        String tab = intent.getStringExtra("tabName");
        return tab == null || tab.isEmpty() || "default".equals(tab);
    }
    private void installCategoryGate() {
        Method gate = targets.method(HookTargets.KEY_CATEGORY_TAB_DISABLED_METHOD);
        if (gate == null || gate.getReturnType() != boolean.class || gate.getParameterTypes().length != 0) return;
        XposedBridge.hookMethod(gate, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (NavigationSettings.visible("BookCategory")) param.setResult(false);
            }
        });
        categorySupported = true;
    }
    private void installRefresh(Class<?> route) {
        if (refreshInstalled) return;
        refreshInstalled = true;
        for (Method method : route.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getReturnType() != void.class || types.length != 1
                    || !"com.dragon.read.rpc.model.BookStoreAlignmentData".equals(types[0].getName())) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) normalize(route);
                }
            });
        }
    }
    private void normalize(Class<?> route) {
        try {
            Object source = XposedHelpers.getStaticObjectField(route, "e");
            Object defaults = XposedHelpers.getStaticObjectField(route, "f");
            if (!(source instanceof List<?>) || !(defaults instanceof List<?>)) throw new IllegalStateException("路由列表契约变化");
            Map<String, Object> supported = new LinkedHashMap<>();
            List<String> original = new ArrayList<>();
            for (Object value : (List<?>) source) {
                if (!(value instanceof Enum<?>)) throw new IllegalStateException("路由不是业务枚举");
                String id = ((Enum<?>) value).name();
                supported.put(id, value); original.add(id);
            }
            // 宿主未过滤默认路由包含当前支持但被云控移出的入口。不能用所有枚举常量虚构Tab。
            for (Object value : (List<?>) defaults) {
                if (!(value instanceof Enum<?>)) throw new IllegalStateException("默认路由不是业务枚举");
                String id = ((Enum<?>) value).name();
                if (!supported.containsKey(id)) supported.put(id, value);
            }
            if (!categorySupported && !original.contains("BookCategory")) supported.remove("BookCategory");
            if (supported.isEmpty()) return;
            NavigationSettings.discover(new ArrayList<>(supported.keySet()), original);
            List<Object> result = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (String id : NavigationSettings.runtimeOrder()) {
                if (supported.containsKey(id)) { result.add(supported.get(id)); names.add(id); }
            }
            if (result.isEmpty()) { XposedBridge.log("FQHook+Navigation: 无可用显示项，保留宿主"); return; }
            effective = names;
            if (!result.equals(source)) {
                XposedHelpers.setStaticObjectField(route, "e", new ArrayList<>(result));
                XposedBridge.log("FQHook+Navigation: 底栏=" + names);
            }
        } catch (Throwable error) { HookUtils.logError("FQHook+Navigation: 配置不兼容，保留宿主: ", error); }
    }
}
