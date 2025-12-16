package com.build4all.order.dto;

import java.math.BigDecimal;

/**
 * CartLine
 *
 * A single “line” in checkout/cart pricing.
 * This DTO is sent from the client (Flutter) to the backend in /api/orders/checkout
 * to represent one item + its requested quantity.
 *
 * How it’s used in your flow:
 * 1) Client sends itemId + quantity (usually only these 2 are required).
 * 2) OrderServiceImpl loads the Item from DB, validates availability/capacity.
 * 3) Backend computes unitPrice + lineSubtotal and sets them back on this object
 *    so CheckoutPricingService (shipping/tax/coupon) can use consistent values.
 *
 * Fields:
 * - itemId: which Item is being purchased/booked (Activity or Product)
 * - quantity: how many units/seats
 * - unitPrice: price per unit (computed server-side from Item.price or Product.getEffectivePrice)
 * - lineSubtotal: unitPrice * quantity (computed server-side)
 * - weightKg: optional weight per unit (or line weight depending on your shipping rules)
 *   - If you use weight-based shipping, ShippingService can read this to compute totals.
 *
 * Notes / Best practice:
 * - For security, client should NOT be trusted for unitPrice/lineSubtotal.
 *   Your current code overwrites them server-side, which is correct.
 * - Decide and document whether weightKg is:
 *   (A) per unit weight, or
 *   (B) total line weight.
 *   If it is per unit, shipping code usually computes: weightKg * quantity.
 */
public class CartLine {

    /** Item id to buy/book */
    private Long itemId;

    /** Quantity requested */
    private int quantity;

    /**
     * Unit price resolved by backend (do not trust client value).
     * For ecommerce products: typically Product.getEffectivePrice().
     * For activities: typically Item.getPrice().
     */
    private BigDecimal unitPrice;

    /**
     * Calculated by backend: unitPrice * quantity.
     * Used by pricing engine (coupons/taxes/shipping).
     */
    private BigDecimal lineSubtotal;

    /**
     * Optional: weight in KG used for shipping calculations.
     * Clarify if this is per-unit weight or already multiplied by quantity.
     */
    private BigDecimal weightKg;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public void setLineSubtotal(BigDecimal lineSubtotal) {
        this.lineSubtotal = lineSubtotal;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }
}
