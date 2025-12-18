package com.build4all.home.sections.dto;

/**
 * SectionAddProductRequest
 *
 * Purpose:
 * - Request DTO used by OWNER/admin endpoints to attach a product to a specific Home section.
 * - This creates (or configures) a link row in the `home_section_products` table.
 *
 * Typical endpoint:
 * - POST /api/home/sections/{sectionCode}/products?ownerProjectId={AUP_ID}
 *
 * Example request body:
 * {
 *   "productId": 101,
 *   "sortOrder": 0,
 *   "active": true
 * }
 *
 * Fields:
 * - productId:
 *     Required.
 *     The Product identifier to link to the section.
 *     Note: Product extends Item, so this is Product.item_id.
 *
 * - sortOrder:
 *     Optional.
 *     Controls the order of products within the section (ascending).
 *     If null, backend should default to 0.
 *
 * - active:
 *     Optional.
 *     Controls visibility of this product inside the section.
 *     If null, backend should default to true.
 *
 * Notes:
 * - This DTO uses "active" which matches the HomeSectionProduct.active field and is consistent with
 *   HomeSection.active and HomeBanner.active.
 * - If you later add an endpoint to update ordering/visibility of an existing link,
 *   you can reuse this DTO (or create a SectionUpdateProductRequest).
 */
public class SectionAddProductRequest {

    private Long productId;
    private Integer sortOrder;
    private Boolean active;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
