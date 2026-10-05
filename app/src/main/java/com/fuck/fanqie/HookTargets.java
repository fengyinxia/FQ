package com.fuck.fanqie;

public final class HookTargets {
    public static final String KEY_ABTEST_METHOD = "method_abtest";
    public static final String KEY_AD_CONFIG_METHOD = "method_ad_config";
    public static final String KEY_AD_FREE_CLASS = "class_ad_free";
    public static final String KEY_AD_FREE_METHOD = "method_ad_free";
    public static final String KEY_AUTHOR_SAY_METHOD = "method_author_say";
    public static final String KEY_BOOKSHELF_BANNER_RESPONSE_METHOD = "method_bookshelf_banner_response";
    public static final String KEY_BOOK_NAME_CLICK_METHOD = "method_book_name_click";
    public static final String KEY_CHAPTER_END_CONTROL_METHOD = "method_chapter_end_control";
    public static final String KEY_CHAPTER_END_HOT_COMMENT_METHOD = "method_chapter_end_hot_comment";
    public static final String KEY_CATEGORY_TAB_DISABLED_METHOD = "method_category_tab_disabled";
    public static final String KEY_CHECK_UPDATE_METHOD = "method_check_update";
    public static final String KEY_COVER_HOT_COMMENT_METHOD = "method_cover_hot_comment";
    public static final String KEY_COVER_TEXT_RENDER_METHOD = "method_cover_text_render";
    public static final String KEY_COVER_IMAGE_RENDER_METHOD = "method_cover_image_render";
    public static final String KEY_CHAPTER_DECRYPT_METHOD = "method_chapter_decrypt";
    public static final String KEY_DYNAMIC_METHOD = "method_dynamic";
    public static final String KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD = "method_download_status_dispatcher";
    public static final String KEY_DOWNLOAD_CLICK_METHOD = "method_download_click";
    public static final String KEY_FEATURE_LIST_LOAD_CLASS = "class_feature_list_load";
    public static final String KEY_QUICK_ACCESS_CONVERT_METHOD = "method_quick_access_convert";
    public static final String KEY_QUICK_ACCESS_AGGREGATE_METHOD = "method_quick_access_aggregate";
    public static final String KEY_FILTER_DATA_METHOD = "method_filter_data";
    public static final String KEY_LUCKY_DOG_METHOD = "method_lucky_dog";
    public static final String KEY_MAIN_ACTIVITY_ON_CREATE_METHOD = "method_main_activity_on_create";
    public static final String KEY_MY_PAGE_CONTENT_METHOD = "method_my_page_content";
    public static final String KEY_MY_PAGE_RECOMMEND_ENABLE_METHOD = "method_my_page_recommend_enable";
    public static final String KEY_MY_PAGE_SEARCH_BAR_METHOD = "method_my_page_search_bar";
    public static final String KEY_MY_PAGE_SETTINGS_CLICK_METHOD = "method_my_page_settings_click";
    public static final String KEY_MY_PAGE_VIP_ENTRANCE_METHOD = "method_my_page_vip_entrance";
    public static final String KEY_POP_METHOD = "method_pop";
    public static final String KEY_RED_DOT_METHOD = "method_red_dot";
    public static final String KEY_READER_DIRECTORY_PRELOAD_CLASS = "class_reader_directory_preload";
    public static final String KEY_SEARCH_BAR_METHOD = "method_search_bar";
    public static final String KEY_SEARCH_CUE_LIST_METHOD = "method_search_cue_list";
    public static final String KEY_SEARCH_CUE_KMP_LIST_METHOD = "method_search_cue_kmp_list";
    public static final String KEY_SPLASH_K1_METHOD = "method_splash_k1";
    public static final String KEY_TAB_METHOD = "method_tab";
    public static final String KEY_TAB_ROUTE_HELPER_CLASS = "class_tab_route_helper";
    public static final String KEY_TOP_TAP_METHOD = "method_top_tap";
    public static final String KEY_UPDATE_METHOD = "method_update";
    public static final String KEY_VIP_INFO_MODEL_CLASS = "class_vip_info_model";

    private static final String[] ALL_KEYS = new String[]{
            KEY_ABTEST_METHOD,
            KEY_AD_CONFIG_METHOD,
            KEY_AD_FREE_CLASS,
            KEY_AD_FREE_METHOD,
            KEY_AUTHOR_SAY_METHOD,
            KEY_BOOKSHELF_BANNER_RESPONSE_METHOD,
            KEY_BOOK_NAME_CLICK_METHOD,
            KEY_CHAPTER_END_CONTROL_METHOD,
            KEY_CHAPTER_END_HOT_COMMENT_METHOD,
            KEY_CATEGORY_TAB_DISABLED_METHOD,
            KEY_CHECK_UPDATE_METHOD,
            KEY_COVER_HOT_COMMENT_METHOD,
            KEY_COVER_TEXT_RENDER_METHOD,
            KEY_COVER_IMAGE_RENDER_METHOD,
            KEY_CHAPTER_DECRYPT_METHOD,
            KEY_DYNAMIC_METHOD,
            KEY_DOWNLOAD_STATUS_DISPATCHER_METHOD,
            KEY_DOWNLOAD_CLICK_METHOD,
            KEY_FEATURE_LIST_LOAD_CLASS,
            KEY_QUICK_ACCESS_CONVERT_METHOD,
            KEY_QUICK_ACCESS_AGGREGATE_METHOD,
            KEY_FILTER_DATA_METHOD,
            KEY_LUCKY_DOG_METHOD,
            KEY_MAIN_ACTIVITY_ON_CREATE_METHOD,
            KEY_MY_PAGE_CONTENT_METHOD,
            KEY_MY_PAGE_RECOMMEND_ENABLE_METHOD,
            KEY_MY_PAGE_SEARCH_BAR_METHOD,
            KEY_MY_PAGE_SETTINGS_CLICK_METHOD,
            KEY_MY_PAGE_VIP_ENTRANCE_METHOD,
            KEY_POP_METHOD,
            KEY_RED_DOT_METHOD,
            KEY_READER_DIRECTORY_PRELOAD_CLASS,
            KEY_SEARCH_BAR_METHOD,
            KEY_SEARCH_CUE_LIST_METHOD,
            KEY_SEARCH_CUE_KMP_LIST_METHOD,
            KEY_SPLASH_K1_METHOD,
            KEY_TAB_METHOD,
            KEY_TAB_ROUTE_HELPER_CLASS,
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
