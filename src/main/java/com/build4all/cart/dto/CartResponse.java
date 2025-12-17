package com.build4all.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO returned by Cart APIs.
 *
 * Endpoints that return this:
 * - GET    /api/cart
 * - POST   /api/cart/items
 * - PUT    /api/cart/items/{cartItemId}
 * - DELETE /api/cart/items/{cartItemId}
 *
 * Purpose:
 * - Provide a UI-friendly snapshot of the user's ACTIVE cart:
 *   cart header info + list of cart items + totals.
 *
 * Notes (as used in CartServiceImpl):
 * - totalPrice is recalculated from items (unitPrice * quantity) before returning.
 * - currencySymbol is extracted from cart.currency (if available).
 * - items is a list of CartItemResponse shaped for mobile/web UI.
 */
public class CartResponse {

    /**
     * Cart identifier (primary key in DB).
     * Used by clients mainly for display/debug; most operations are per-user (from JWT).
     */
    private Long cartId;

    /**
     * Current cart status as String (e.g. "ACTIVE", "CONVERTED").
     * In DB it is stored as enum CartStatus, but returned as text for frontend simplicity.
     */
    private String status;

    /**
     * Cart total price (sum of all line totals).
     * This is usually: Σ (unitPrice * quantity) for each cart item.
     */
    private BigDecimal totalPrice;

    /**
     * Currency symbol for display (e.g. "$", "€", "LBP").
     * Filled from cart.currency if present; can be null if currency not set.
     */
    private String currencySymbol;

    /**
     * List of items currently in the cart (UI-ready lines).
     */
    private List<CartItemResponse> items;

    // getters & setters

    public Long getCartId() { return cartId; }
    public void setCartId(Long cartId) { this.cartId = cartId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }

    public List<CartItemResponse> getItems() { return items; }
    public void setItems(List<CartItemResponse> items) { this.items = items; }
}
