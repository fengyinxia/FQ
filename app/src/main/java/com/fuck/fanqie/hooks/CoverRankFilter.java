package com.fuck.fanqie.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** 只给封面评分区装饰器提供展示副本，不改源数据及其他排行榜。 */
final class CoverRankFilter {
    private CoverRankFilter() { }

    static Object withoutRank(Object source) throws ReflectiveOperationException {
        if (source == null) return null;
        Field infoField = null;
        for (Field field : source.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && "com.dragon.read.reader.bookcover.BookCoverInfo".equals(field.getType().getName())) {
                if (infoField != null) throw new IllegalStateException("封面数据字段不唯一");
                infoField = field;
            }
        }
        if (infoField == null) throw new IllegalStateException("封面数据字段缺失");
        Object info = infoField.get(source);
        if (info == null) return source;
        Object displayInfo = normalInfo(info);
        if (displayInfo == info) return source;
        Object display = copy(source);
        infoField.set(display, displayInfo);
        return display;
    }

    static Object normalInfo(Object info) throws ReflectiveOperationException {
        if (info == null) return null;
        Field rank = info.getClass().getField("dianfengRankLabel");
        if (rank.get(info) == null) return info;
        Object display = copy(info);
        rank.set(display, null);
        return display;
    }

    private static Object copy(Object source) throws ReflectiveOperationException {
        Object result = source.getClass().getDeclaredConstructor().newInstance();
        for (Field field : source.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            field.set(result, field.get(source));
        }
        return result;
    }
}
