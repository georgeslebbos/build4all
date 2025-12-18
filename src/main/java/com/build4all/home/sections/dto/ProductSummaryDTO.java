package com.build4all.home.sections.dto;

import java.math.BigDecimal;

/**
 * ProductSummaryDTO
 *
 * Purpose:
 * - Lightweight product representation for the Home page sections.
 * - Used in HomeSectionResponse.products so the frontend can render product cards
 *   without needing the full Product entity payload.
 *
 * Why a "summary" DTO?
 * - Keeps the home page response fast and small.
 * - Avoids exposing internal fields that are not needed on the home screen.
 * - Allows the frontend to render a list of products with essential details only.
 *
 * Fields:
 * - id:
 *     Product identifier (Product.item_id) because Product extends Item.
 *     This id is used for navigation to Product Details screen.
 *
 * - name:
 *     Display name of the product (Item.item_name).
 *
 * - price:
 *     Base/original price (Item.price).
 *
 * - salePrice:
 *     Discounted price (Item.sale_price).
 *     May be null if the product is not on sale.
 *     Frontend typically displays:
 *       - if salePrice != null and salePrice < price: show price as strikethrough + salePrice
 *       - else show price only
 *
 * - imageUrl:
 *     Main product image (Item.image_url).
 *     Can be null/empty; frontend should show a placeholder image in that case.
 *
 * Notes:
 * - If you later want the backend to compute discount state, you can extend this DTO with:
 *     boolean onSaleNow;
 *     BigDecimal effectivePrice;
 *   using Item.isOnSaleNow() and Item.getEffectivePrice().
 */
public class ProductSummaryDTO {

    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final BigDecimal salePrice;
    private final String imageUrl;

    public ProductSummaryDTO(Long id, String name, BigDecimal price, BigDecimal salePrice, String imageUrl) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.salePrice = salePrice;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getSalePrice() { return salePrice; }
    public String getImageUrl() { return imageUrl; }
}
