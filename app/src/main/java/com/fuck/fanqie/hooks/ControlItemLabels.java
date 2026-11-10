package com.fuck.fanqie.hooks;

/** 只映射业务身份，不保存弹窗正文、Banner 书名或跳转链接。 */
final class ControlItemLabels {
    private ControlItemLabels() { }

    static String banner(String type) {
        switch (type) {
            case "ChaseBookUpdate": return "追更提醒";
            case "LongTimeNotRead": return "久未阅读提醒";
            case "Preheat": return "预热提醒";
            case "BookEnd": return "完结提醒";
            case "ExtraChapters": return "番外提醒";
            case "SubscribeMediaBookLaunch": return "订阅有声书上线";
            case "PublishLottery": return "出版抽奖";
            case "AuthorNewBook": return "作者新书";
            case "Subscribe": return "订阅提醒";
            case "SubscribeExposed": return "订阅展示";
            case "Commerce": return "电商 Banner";
            case "DeepReadingBookUpdate": return "深度阅读更新";
            case "MotionComicUrge": return "漫剧催更";
            case "RelateVideo": return "关联短剧";
            case "WishVideo": return "心愿视频";
            case "BookEndFeed": return "完结推荐";
            case "ChaseBookPost": return "追更动态";
            case "RelateNovel": return "关联小说";
            case "BookEndRelateVideo": return "完结关联短剧";
            case "SeriesReserveOriginalBook": return "短剧预约原著";
            case "VirtualBanner": return "虚拟 Banner";
            default: return type;
        }
    }

    static String popupIdentity(Object id) {
        if (!(id instanceof String)) return null;
        String value = (String) id;
        // 空值、URL 或含动态参数的未知身份不入库、不屏蔽。
        if (value.isEmpty() || value.length() > 160 || !value.matches("[A-Za-z0-9_./:-]+")) return null;
        if (value.contains("://") || "null".equalsIgnoreCase(value)) return null;
        return "id/" + value;
    }

    static String popupLabel(Object name, Object id) {
        String fallback = String.valueOf(id);
        if (!(name instanceof String)) return fallback;
        String label = ((String) name).trim();
        if (label.isEmpty() || label.length() > 48 || !label.matches("[\\p{L}\\p{N}_ .:/-]+")
                || label.contains("://")) return fallback;
        String result = label.equals(fallback) ? label : label + " · " + fallback;
        return result.length() <= 64 ? result : fallback;
    }
}
