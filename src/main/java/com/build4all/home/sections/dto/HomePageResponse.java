package com.build4all.home.sections.dto;

import com.build4all.home.banner.dto.HomeBannerResponse;

import java.util.List;

/**
 * HomePageResponse
 *
 * Purpose:
 * - Aggregated response DTO for the Home screen.
 * - Returned by the endpoint:
 *     GET /api/home?ownerProjectId={AUP_ID}
 *
 * Why aggregated?
 * - Home page needs multiple content blocks (top banners + sections).
 * - Returning everything in one payload allows the frontend to:
 *   - make a single API call
 *   - render the page in the correct order:
 *       1) banners at the top
 *       2) sections below
 *
 * Fields:
 * - banners:
 *     List of banner items to show in the top slider/hero area.
 *     Uses HomeBannerResponse from the existing banner module.
 *
 * - sections:
 *     List of sections that appear below the banners.
 *     Each section includes metadata (code/title/layout/sortOrder)
 *     and the products to render inside it (ProductSummaryDTO list).
 *
 * Ordering:
 * - Backend typically returns banners sorted by banner.sortOrder.
 * - Backend returns sections sorted by section.sortOrder (ascending).
 * - Products inside each section are also sorted by link.sortOrder (ascending).
 *
 * Frontend rendering expectation:
 * - Render banners first (slider)
 * - Then iterate sections in order and render each section based on layout:
 *     HORIZONTAL -> horizontal list
 *     GRID       -> grid list
 */
public class HomePageResponse {

    private final List<HomeBannerResponse> banners;
    private final List<HomeSectionResponse> sections;

    public HomePageResponse(List<HomeBannerResponse> banners, List<HomeSectionResponse> sections) {
        this.banners = banners;
        this.sections = sections;
    }

    public List<HomeBannerResponse> getBanners() { return banners; }
    public List<HomeSectionResponse> getSections() { return sections; }
}
