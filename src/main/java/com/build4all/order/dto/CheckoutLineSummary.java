package com.build4all.order.dto;

import java.math.BigDecimal;

/**
 * CheckoutLineSummary
 * <p>
 * DTO returned inside CheckoutSummaryResponse.lines.
 * It represents ONE cart/order line in the checkout breakdown (per item).
 * <p>
 * This class is used for:
 * - Showing the user a per-item breakdown in the checkout screen
 * - Returning the exact unitPrice and subtotal used in pricing
 * - Keeping the response lightweight (no heavy Item entity / no lazy-loading issues)
 * <p>
 * Fields meaning:
 * - itemId: the item/product identifier being purchased
 * - itemName: optional display name (can be filled by server or client; in pricing service it may be null)
 * - quantity: how many units / seats / pieces
 * - unitPrice: price of ONE unit (after discount at item level, if any)
 * - lineSubtotal: unitPrice * quantity (before shipping/tax/coupon)
 * <p>
 * Notes:
 * - BigDecimal is used for money to avoid floating point rounding issues.
 * - itemName is intentionally optional to keep pricing engine independent from catalog lookups.
 */
public class CheckoutLineSummary {

    /**
     * The purchased item id (Item/Product/Activity...)
     */
    private Long itemId;

    /**
     * Optional: a friendly display name for UI
     */
    private String itemName;

    /**
     * Quantity of this item
     */
    private int quantity;

    /**
     * Price of one unit (money)
     */
    private BigDecimal unitPrice;

    /**
     * Subtotal for this line = unitPrice * quantity
     */
    private BigDecimal lineSubtotal;

    /**
     * No-arg constructor needed by Jackson (JSON serialization/deserialization).
     */
    public CheckoutLineSummary() {
    }

    /**
     * Full constructor used when building checkout response.
     *
     * @param itemId       the item identifier
     * @param itemName     display name (nullable)
     * @param quantity     number of units
     * @param unitPrice    price per unit
     * @param lineSubtotal unitPrice * quantity
     */
    public CheckoutLineSummary(Long itemId,
                               String itemName,
                               int quantity,
                               BigDecimal unitPrice,
                               BigDecimal lineSubtotal) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineSubtotal = lineSubtotal;
    }

    /**
     * @return item id
     */
    public Long getItemId() {
        return itemId;
    }

    /**
     * @param itemId item id
     */
    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    /**
     * @return optional item name
     */
    public String getItemName() {
        return itemName;
    }

    /**
     * @param itemName optional item name
     */
    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    /**
     * @return quantity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * @param quantity quantity
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /**
     * @return unit price
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * @param unitPrice unit price
     */
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * @return line subtotal (unitPrice * quantity)
     */
    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    /**
     * @param lineSubtotal line subtotal
     */
    public void setLineSubtotal(BigDecimal lineSubtotal) {
        this.lineSubtotal = lineSubtotal;
    }
}
