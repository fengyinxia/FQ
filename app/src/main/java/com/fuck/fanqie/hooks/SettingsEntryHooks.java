package com.fuck.fanqie.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import com.fuck.fanqie.HookTargets;
import com.fuck.fanqie.cache.CachedTargets;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** 只添加设置入口，不控制现有功能；原生/KMP 共用宿主通用设置项。 */
public final class SettingsEntryHooks extends BaseHook {
    private final CachedTargets targets;
    private Method pageCreate;
    private final Set<Object> entries = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    public SettingsEntryHooks(CachedTargets targets, ClassLoader loader) {
        super(loader);
        this.targets = targets;
    }

    @Override
    public void apply() {
        try {
            pageCreate = targets.method(HookTargets.KEY_SETTINGS_PAGE_CREATE_METHOD);
            if (!PluginSettingsPage.install(pageCreate, targets.method(HookTargets.KEY_SKIN_NIGHT_METHOD),
                    targets.method(HookTargets.KEY_SKIN_CHANGE_METHOD))) {
                return;
            }
            Class<?> item = targets.type(HookTargets.KEY_SETTINGS_ITEM_CLASS);
            if (item == null) {
                XposedBridge.log("FQHook+Settings: 通用设置项目标缺失，保留宿主");
                return;
            }
            Constructor<?> constructor = item.getDeclaredConstructor();
            constructor.setAccessible(true);
            // 7.4.3.32 通用模型字段契约，来自锚点构造及原生/KMP 点击消费链。
            // 类由 DexKit 定位；字段名称尚依赖本版，类型不兼容则不注入。
            Field title = field(item, "e", CharSequence.class);
            Field arrow = field(item, "i", boolean.class);
            Field click = item.getDeclaredField("n");
            Class<?> listener = click.getType();
            if (Modifier.isStatic(click.getModifiers()) || !listener.isInterface()) {
                throw new IllegalStateException("设置项点击字段不是实例接口");
            }
            Method[] callbacks = listener.getDeclaredMethods();
            if (callbacks.length != 1) {
                throw new IllegalStateException("设置项点击接口不唯一");
            }
            Method callback = callbacks[0];
            Class<?>[] args = callback.getParameterTypes();
            if (callback.getReturnType() != void.class || args.length != 3
                    || args[0] != View.class || args[1] != item || args[2] != int.class) {
                throw new IllegalStateException("设置项点击签名不兼容");
            }
            click.setAccessible(true);
            install(HookTargets.KEY_SETTINGS_NATIVE_INSERT_METHOD, constructor, title, arrow, click, callback);
            install(HookTargets.KEY_SETTINGS_KMP_INSERT_METHOD, constructor, title, arrow, click, callback);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Settings: 设置项契约不兼容，保留宿主: ", throwable);
        }
    }

    private static Field field(Class<?> type, String name, Class<?> expected) throws Exception {
        Field field = type.getDeclaredField(name);
        if (Modifier.isStatic(field.getModifiers()) || field.getType() != expected) {
            throw new IllegalStateException("设置项字段不兼容: " + name);
        }
        field.setAccessible(true);
        return field;
    }

    private void install(String key, Constructor<?> constructor, Field title, Field arrow,
                         Field click, Method callback) {
        try {
            Method method = targets.method(key);
            if (method == null) {
                XposedBridge.log("FQHook+Settings: 插入目标缺失: " + key);
                return;
            }
            Class<?>[] args = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || args.length != 2 || !List.class.isAssignableFrom(args[0])
                    || !List.class.isAssignableFrom(args[1])) {
                throw new IllegalStateException("设置分组插入签名不兼容");
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        return;
                    }
                    try {
                        insert(param.args, constructor, title, arrow, click, callback);
                    } catch (Throwable throwable) {
                        HookUtils.logError("FQHook+Settings: 入口注入失败 " + key + ": ", throwable);
                    }
                }
            });
            XposedBridge.log("FQHook+Settings: 已安装入口: " + key);
        } catch (Throwable throwable) {
            HookUtils.logError("FQHook+Settings: 入口挂载失败 " + key + ": ", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private void insert(Object[] args, Constructor<?> constructor, Field title, Field arrow,
                        Field click, Method callback) throws Exception {
        if (!(args[0] instanceof List<?>) || !(args[1] instanceof List<?>) || args[0] == args[1]) {
            throw new IllegalStateException("设置总列表或分组缺失");
        }
        List<Object> all = (List<Object>) args[0];
        List<Object> group = (List<Object>) args[1];
        for (Object value : all) {
            if (entries.contains(value)) {
                return;
            }
        }
        // 当前分组必须是总列表尾部的同一批对象；不要在不明确的位置插入。
        if (all.size() < group.size()) {
            throw new IllegalStateException("设置分组长度无效");
        }
        int start = all.size() - group.size();
        java.util.IdentityHashMap<Object, Integer> tail = new java.util.IdentityHashMap<>();
        for (int i = start; i < all.size(); i++) {
            Object value = all.get(i);
            Integer count = tail.get(value);
            tail.put(value, count == null ? 1 : count + 1);
        }
        // 宿主的默认启动项可能在分组内调序，但总列表仍追加到尾部。
        for (Object value : group) {
            Integer count = tail.get(value);
            if (count == null || count == 0) {
                throw new IllegalStateException("设置分组不在列表尾部");
            }
            tail.put(value, count - 1);
        }
        Object entry = constructor.newInstance();
        title.set(entry, "FQ 插件设置");
        arrow.setBoolean(entry, true);
        Object listener = Proxy.newProxyInstance(click.getType().getClassLoader(),
                new Class<?>[]{click.getType()}, (proxy, method, values) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "equals": return proxy == values[0];
                            case "hashCode": return System.identityHashCode(proxy);
                            case "toString": return "FQSettingsEntryListener";
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    }
                    if (!method.equals(callback) || values == null || !(values[0] instanceof View)) {
                        throw new IllegalStateException("未知设置点击回调");
                    }
                    Activity activity = activity(((View) values[0]).getContext());
                    if (activity == null) {
                        XposedBridge.log("FQHook+Settings: 点击上下文不是 Activity，跳过页面");
                    } else {
                        PluginSettingsPage.open(activity, pageCreate);
                    }
                    return null;
                });
        click.set(entry, listener);
        // 双列表保持一致；第二次插入失败时撤回第一次，防止残缺设置行。
        int index = all.size();
        all.add(entry);
        try {
            group.add(entry);
        } catch (RuntimeException failure) {
            all.remove(index);
            throw failure;
        }
        entries.add(entry);
        XposedBridge.log("FQHook+Settings: 已插入 FQ 插件设置");
    }

    private static Activity activity(Context context) {
        Set<Context> visited = Collections.newSetFromMap(new java.util.IdentityHashMap<Context, Boolean>());
        while (context != null && visited.add(context)) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = context instanceof ContextWrapper ? ((ContextWrapper) context).getBaseContext() : null;
        }
        return null;
    }
}
