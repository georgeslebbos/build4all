package com.build4all.home.sections.dto;

/**
 * BannerDTO
 *
 * Purpose:
 * - Lightweight DTO used by the Home page response to represent a banner item
 *   in a format that is easy for the frontend to render.
 *
 * Typical usage:
 * - Returned inside a "home page" payload (top slider / hero banners).
 * - The frontend renders:
 *     - imageUrl as the banner image
 *     - title as the overlay text (optional)
 * - On tap/click, the frontend uses actionType + actionValue to navigate.
 *
 * Fields:
 * - id:
 *     A string identifier of the banner (often the DB id converted to string).
 *     Note: In your HomeBanner entity, id is Long. Converting to String is fine
 *     for UI usage, but keep it consistent across the app.
 *
 * - imageUrl:
 *     The banner image URL (can be relative like /uploads/... or absolute).
 *
 * - title:
 *     Short title shown on the banner (optional).
 *
 * - actionType:
 *     The type of action to perform when the banner is clicked.
 *     Examples (align with your HomeBanner.targetType):
 *       - PRODUCT
 *       - CATEGORY
 *       - URL
 *       - NONE
 *
 * - actionValue:
 *     The value associated with the action:
 *       - if PRODUCT  -> productId as string
 *       - if CATEGORY -> categoryId as string
 *       - if URL      -> a full URL
 *       - if NONE     -> null/empty
 *
 * Notes / Alignment:
 * - Your HomeBanner already supports:
 *     targetType, targetId, targetUrl
 * - If you keep BannerDTO, your mapping logic should be:
 *     actionType  = targetType
 *     actionValue = (targetType == "URL") ? targetUrl : String.valueOf(targetId)
 */
public class BannerDTO {

    private final String id;
    private final String imageUrl;
    private final String title;
    private final String actionType;
    private final String actionValue;

    public BannerDTO(String id, String imageUrl, String title, String actionType, String actionValue) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.title = title;
        this.actionType = actionType;
        this.actionValue = actionValue;
    }

    public String getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public String getTitle() { return title; }
    public String getActionType() { return actionType; }
    public String getActionValue() { return actionValue; }
}
