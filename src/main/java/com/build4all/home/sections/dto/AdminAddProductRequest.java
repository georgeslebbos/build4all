package com.build4all.home.sections.dto;

/**
 * AdminAddProductRequest
 *
 * Purpose:
 * - Request DTO used by OWNER/admin endpoints to attach a product to a Home Section.
 * - This DTO represents a row in the "home_section_products" link table from the admin perspective.
 *
 * Typical usage:
 * - Endpoint example:
 *     POST /api/home/sections/{sectionCode}/products?ownerProjectId=123
 * - Body example:
 *     {
 *       "productId": 101,
 *       "sortOrder": 0,
 *       "enabled": true
 *     }
 *
 * Fields:
 * - productId:
 *     The product identifier to add to the section.
 *     Note: in your model, Product extends Item, so this is the Product's item_id.
 *
 * - sortOrder:
 *     Controls ordering of products inside the section.
 *     Lower values appear first (ascending).
 *     Optional; if null, backend should default to 0.
 *
 * - enabled:
 *     Whether the product should be visible in this section.
 *     Optional; if null, backend should treat it as true (enabled).
 *
 * Notes / Alignment:
 * - Your entity uses the column name "active" (boolean) in HomeSectionProduct.
 * - This DTO uses "enabled" which is fine, but you should map:
 *     enabled -> active
 *   in the service/controller when creating/updating the link.
 *
 * Recommendation:
 * - For consistency across the project, you may rename "enabled" to "active"
 *   (since banners/sections also use active), but not required.
 */
public class AdminAddProductRequest {

    /** The product to add to the section (Product.item_id). */
    public Long productId;

    /** Ordering inside the section (ascending). */
    public Integer sortOrder;

    /** Visibility flag from admin side; should be mapped to HomeSectionProduct.active. */
    public Boolean enabled;
}
