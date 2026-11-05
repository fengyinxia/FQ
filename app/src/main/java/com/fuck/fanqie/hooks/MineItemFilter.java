package com.fuck.fanqie.hooks;

import com.fuck.fanqie.DynamicItems;
import com.fuck.fanqie.PluginSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** 业务枚举为身份；名称映射仅负责展示，未发现的枚举不会凭空进入目录。 */
final class MineItemFilter {
    private MineItemFilter() { }

    static boolean card(Object item) throws IllegalAccessException {
        if (item == null) return false;
        Field found = null;
        for (Field field : item.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType().isEnum()
                    && "com.dragon.read.component.biz.api.model.CardType".equals(field.getType().getName())) {
                if (found != null) throw new IllegalStateException("CardType 字段不唯一");
                found = field;
            }
        }
        if (found == null) return false;
        found.setAccessible(true);
        Object type = found.get(item);
        if (!(type instanceof Enum<?>)) return false;
        String name = ((Enum<?>) type).name();
        // QUICK_ACCESS 是容器，不能与子入口作为同一屏蔽行。
        if ("QUICK_ACCESS".equals(name)) return false;
        return observe(name, null);
    }

    static boolean observe(String name, String nativeTitle) {
        boolean legacy = false;
        switch (name) {
            case "AD_ORDER": case "WRITER_CENTER": case "VIDEO_BOOKING": case "MINE_LIKE":
            case "MINI_GAME": case "FEEDBACK_AND_HELP": case "COMMON":
                legacy = PluginSettings.enabled("mine_quick"); break;
            case "VIP": legacy = PluginSettings.enabled("mine_vip") || PluginSettings.enabled("mine_quick"); break;
            default: break; // 仅迁移已经核对的旧项目，新增类型默认放行。
        }
        return DynamicItems.observe(DynamicItems.MINE, "card/" + name,
                nativeTitle == null ? label(name) : nativeTitle, legacy);
    }

    static String label(String type) {
        switch (type) {
            case "READING_HISTORY": return "浏览历史";
            case "READING_PREFERENCE": return "阅读偏好";
            case "BOOK_DOWNLOAD": return "我的下载";
            case "MY_MESSAGE": return "我的消息";
            case "MY_FOLLOW": return "关注入口";
            case "AD_ORDER": return "广告订单";
            case "WRITER_CENTER": return "作家中心";
            case "VIDEO_BOOKING": return "短剧预约";
            case "MINE_LIKE": return "我的喜欢";
            case "MINI_GAME": return "小游戏";
            case "VIP": return "会员入口";
            case "FEEDBACK_AND_HELP": return "反馈与帮助";
            default: return type;
        }
    }
}
