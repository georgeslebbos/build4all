package com.build4all.home.sections.dto;

/**
 * AdminCreateSectionRequest
 *
 * Purpose:
 * - Request DTO used by OWNER/admin endpoints to create (or update) a Home Section.
 * - Represents the admin-configurable properties of a HomeSection entity.
 *
 * Typical usage:
 * - Endpoint example:
 *     POST /api/home/sections
 * - Body example:
 *     {
 *       "code": "flash_sale",
 *       "title": "Flash Sale",
 *       "layout": "HORIZONTAL",
 *       "sortOrder": 0,
 *       "enabled": true
 *     }
 *
 * Fields:
 * - code:
 *     Stable identifier of the section.
 *     Used in URLs and backend lookups (e.g., /sections/{sectionCode}/products).
 *     Must be unique per tenant (AUP) in DB due to unique constraint (aup_id, code).
 *     Recommended format: snake_case (flash_sale, new_arrivals, featured).
 *
 * - title:
 *     Human-readable label shown on the home page.
 *     Optional (frontend could choose to hide title or derive it from code).
 *
 * - layout:
 *     How frontend should render products inside the section:
 *     - HORIZONTAL: a horizontal carousel/list.
 *     - GRID: a grid of items (e.g., 2 columns).
 *     Backend should map this string to HomeSectionLayout enum (case-insensitive).
 *
 * - sortOrder:
 *     Ordering of sections on the home page (ascending).
 *     Lower values appear first.
 *     Optional; if null, backend should default to 0.
 *
 * - enabled:
 *     Whether the section is visible on the public home page.
 *     Optional; if null, backend should treat it as true (enabled).
 *
 * Notes / Alignment:
 * - Your entity uses "active" while this DTO uses "enabled".
 *   Backend mapping should do:
 *     enabled -> active
 *
 * Recommendation:
 * - For consistency with other modules (banners/sections/products links),
 *   consider renaming enabled to active; but keeping enabled is OK if you map properly.
 */
public class AdminCreateSectionRequest {

    /** Stable unique code of the section per tenant (AUP). */
    public String code;

    /** Display title for the section (shown in UI). */
    public String title;

    /** Layout mode: "HORIZONTAL" or "GRID". */
    public String layout;   // HORIZONTAL / GRID

    /** Ordering of sections on the home page (ascending). */
    public Integer sortOrder;

    /** Visibility from admin side; should be mapped to HomeSection.active. */
    public Boolean enabled;
}
