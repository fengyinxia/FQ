package com.fuck.fanqie;

public final class HookTargets {
    public static final String KEY_ABTEST_METHOD = "method_abtest";
    public static final String KEY_AD_CONFIG_METHOD = "method_ad_config";
    public static final String KEY_AD_FREE_CLASS = "class_ad_free";
    public static final String KEY_AD_FREE_METHOD = "method_ad_free";
    public static final String KEY_AUTHOR_SAY_METHOD = "method_author_say";
    public static final String KEY_BOOK_NAME_CLICK_METHOD = "method_book_name_click";
    public static final String KEY_CHAPTER_END_CONTROL_METHOD = "method_chapter_end_control";
    public static final String KEY_CHAPTER_END_HOT_COMMENT_METHOD = "method_chapter_end_hot_comment";
    public static final String KEY_CHECK_UPDATE_METHOD = "method_check_update";
    public static final String KEY_COVER_HOT_COMMENT_METHOD = "method_cover_hot_comment";
    public static final String KEY_DYNAMIC_METHOD = "method_dynamic";
    public static final String KEY_FEATURE_LIST_LOAD_CLASS = "class_feature_list_load";
    public static final String KEY_FILTER_BANNER_METHOD = "method_filter_banner";
    public static final String KEY_FILTER_DATA_METHOD = "method_filter_data";
    public static final String KEY_GAME_AREA_METHOD = "method_game_area";
    public static final String KEY_LUCKY_DOG_METHOD = "method_lucky_dog";
    public static final String KEY_MSG_AREA_METHOD = "method_msg_area";
    public static final String KEY_MY_PAGE_SEARCH_BAR_METHOD = "method_my_page_search_bar";
    public static final String KEY_MY_PAGE_VIP_ENTRANCE_METHOD = "method_my_page_vip_entrance";
    public static final String KEY_POP_METHOD = "method_pop";
    public static final String KEY_RED_DOT_METHOD = "method_red_dot";
    public static final String KEY_REMOVE_RANK_METHOD = "method_remove_rank";
    public static final String KEY_SEARCH_BAR_METHOD = "method_search_bar";
    public static final String KEY_SPLASH_K1_METHOD = "method_splash_k1";
    public static final String KEY_TAB_METHOD = "method_tab";
    public static final String KEY_TOP_TAP_METHOD = "method_top_tap";
    public static final String KEY_UPDATE_METHOD = "method_update";
    public static final String KEY_VIP_INFO_MODEL_CLASS = "class_vip_info_model";

    private static final String[] ALL_KEYS = new String[]{
            KEY_ABTEST_METHOD,
            KEY_AD_CONFIG_METHOD,
            KEY_AD_FREE_CLASS,
            KEY_AD_FREE_METHOD,
            KEY_AUTHOR_SAY_METHOD,
            KEY_BOOK_NAME_CLICK_METHOD,
            KEY_CHAPTER_END_CONTROL_METHOD,
            KEY_CHAPTER_END_HOT_COMMENT_METHOD,
            KEY_CHECK_UPDATE_METHOD,
            KEY_COVER_HOT_COMMENT_METHOD,
            KEY_DYNAMIC_METHOD,
            KEY_FEATURE_LIST_LOAD_CLASS,
            KEY_FILTER_BANNER_METHOD,
            KEY_FILTER_DATA_METHOD,
            KEY_GAME_AREA_METHOD,
            KEY_LUCKY_DOG_METHOD,
            KEY_MSG_AREA_METHOD,
            KEY_MY_PAGE_SEARCH_BAR_METHOD,
            KEY_MY_PAGE_VIP_ENTRANCE_METHOD,
            KEY_POP_METHOD,
            KEY_RED_DOT_METHOD,
            KEY_REMOVE_RANK_METHOD,
            KEY_SEARCH_BAR_METHOD,
            KEY_SPLASH_K1_METHOD,
            KEY_TAB_METHOD,
            KEY_TOP_TAP_METHOD,
            KEY_UPDATE_METHOD,
            KEY_VIP_INFO_MODEL_CLASS
    };

    private HookTargets() {
    }

    public static String[] allKeys() {
        return ALL_KEYS.clone();
    }
}
