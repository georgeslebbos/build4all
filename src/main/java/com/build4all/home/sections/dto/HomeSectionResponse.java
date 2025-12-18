package com.build4all.home.sections.dto;

import java.util.List;

/**
 * HomeSectionResponse
 *
 * Purpose:
 * - Response DTO representing a single Home section returned to the frontend.
 * - Used inside the aggregated HomePageResponse / HomePublicResponse.
 *
 * What the frontend uses it for:
 * - Render a section header (title)
 * - Decide the UI layout using "layout"
 * - Render the ordered list of products inside the section
 *
 * Fields:
 * - code:
 *     Stable identifier of the section (unique per tenant).
 *     Used for programmatic references (analytics, deep links, admin operations).
 *     Examples: "flash_sale", "featured", "new_arrivals".
 *
 * - title:
 *     Human readable title displayed in UI (can be null/empty).
 *
 * - layout:
 *     Layout type as string, typically the enum name from HomeSectionLayout:
 *       "HORIZONTAL" or "GRID".
 *     Frontend rendering rule:
 *       - HORIZONTAL: horizontal carousel/list
 *       - GRID: grid of products
 *
 * - sortOrder:
 *     Section order on the home page (ascending). Lower appears first.
 *
 * - products:
 *     Ordered list of product cards to display in this section.
 *     Each product is a ProductSummaryDTO (id, name, price, salePrice, imageUrl).
 *
 * Ordering:
 * - Backend returns sections ordered by HomeSection.sortOrder.
 * - Backend returns products ordered by HomeSectionProduct.sortOrder.
 */
public class HomeSectionResponse {

    private final String code;
    private final String title;
    private final String layout; // HORIZONTAL / GRID
    private final int sortOrder;
    private final List<ProductSummaryDTO> products;

    public HomeSectionResponse(String code, String title, String layout, int sortOrder, List<ProductSummaryDTO> products) {
        this.code = code;
        this.title = title;
        this.layout = layout;
        this.sortOrder = sortOrder;
        this.products = products;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getLayout() { return layout; }
    public int getSortOrder() { return sortOrder; }
    public List<ProductSummaryDTO> getProducts() { return products; }
}
