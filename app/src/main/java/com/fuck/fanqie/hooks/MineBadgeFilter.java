package com.fuck.fanqie.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** 仅给侧栏展示转换传递副本，绝不清除原消息未读计数。 */
final class MineBadgeFilter {
    private MineBadgeFilter() { }

    static Object sidebar(Object value) throws ReflectiveOperationException {
        if (!(value instanceof List<?>)) return value;
        List<Object> result = new ArrayList<>(((List<?>) value).size());
        boolean changed = false;
        for (Object item : (List<?>) value) {
            if (item == null || !"com.dragon.read.rpc.model.MsgGroup".equals(item.getClass().getName())) {
                result.add(item);
                continue;
            }
            Class<?> type = item.getClass();
            Field count = type.getField("unreadCount");
            Field show = type.getField("showUnreadCount");
            if (count.getType() != int.class || show.getType() != boolean.class) {
                throw new IllegalStateException("侧栏未读展示字段类型不兼容");
            }
            if (count.getInt(item) == 0 && !show.getBoolean(item)) {
                result.add(item);
                continue;
            }
            Object copy = type.getDeclaredConstructor().newInstance();
            for (Field field : type.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) field.set(copy, field.get(item));
            }
            count.setInt(copy, 0);
            show.setBoolean(copy, false);
            result.add(copy);
            changed = true;
        }
        return changed ? result : value;
    }
}
