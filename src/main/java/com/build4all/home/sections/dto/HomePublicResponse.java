package com.build4all.home.sections.dto;

import java.util.List;

/**
 * HomePublicResponse
 *
 * Purpose:
 * - Public-facing home page response DTO.
 * - Similar to HomePageResponse, but uses a lightweight BannerDTO instead of HomeBannerResponse.
 *
 * Why have a separate "public" response?
 * - Sometimes you want the public API to expose fewer fields than the admin/domain DTO.
 * - BannerDTO is a simplified representation focused on UI needs:
 *     - image + title
 *     - click action (actionType/actionValue)
 *
 * Typical usage:
 * - Returned by an endpoint like:
 *     GET /api/home/public?ownerProjectId={AUP_ID}
 *   (or GET /api/home if you decide to use this DTO there)
 *
 * Fields:
 * - banners:
 *     Top slider banners (existing feature), simplified for the UI.
 *     BannerDTO contains: id, imageUrl, title, actionType, actionValue.
 *
 * - sections:
 *     Home sections displayed below banners.
 *     Each HomeSectionResponse includes:
 *       - code, title, layout, sortOrder
 *       - products (ProductSummaryDTO)
 *
 * Ordering:
 * - banners should be sorted by banner sortOrder (backend responsibility).
 * - sections should be sorted by section sortOrder (backend responsibility).
 * - products inside each section should be sorted by link sortOrder (backend responsibility).
 *
 * Frontend expectations:
 * - Render banners first (carousel)
 * - Render sections afterward, switching UI based on layout:
 *     HORIZONTAL -> horizontal carousel
 *     GRID       -> grid
 *
 * Notes / Alignment:
 * - Your current HomePageResponse already uses HomeBannerResponse.
 * - If you keep HomePublicResponse, decide ONE public contract and use it consistently
 *   to avoid confusion in the Flutter layer.
 */
public class HomePublicResponse {

    private final List<BannerDTO> banners;             // top banners (existing)
    private final List<HomeSectionResponse> sections;  // below

    public HomePublicResponse(List<BannerDTO> banners, List<HomeSectionResponse> sections) {
        this.banners = banners;
        this.sections = sections;
    }

    public List<BannerDTO> getBanners() { return banners; }
    public List<HomeSectionResponse> getSections() { return sections; }
}
