package com.build4all.app.dto;

public record CiBuildConfigDto(
        String OWNER_ID,
        String PROJECT_ID,
        String OWNER_PROJECT_LINK_ID,
        String SLUG,

        String APP_NAME,
        String APP_TYPE,

        Long THEME_ID,
        String THEME_JSON,
        String THEME_JSON_B64,

        Long CURRENCY_ID,
        String CURRENCY_CODE,
        String CURRENCY_SYMBOL,

        String LOGO_URL,

        String API_BASE_URL_OVERRIDE,

        String NAV_JSON,
        String HOME_JSON,
        String ENABLED_FEATURES_JSON,
        String BRANDING_JSON,

        String NAV_JSON_B64,
        String HOME_JSON_B64,
        String ENABLED_FEATURES_JSON_B64,
        String BRANDING_JSON_B64
) {}
