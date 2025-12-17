package com.build4all.cart.dto;

/**
 * Request DTO used by the API when a user adds an item to their cart.
 *
 * Typical usage:
 * - POST /api/cart/items
 * - Body: { "itemId": 123, "quantity": 2 }
 *
 * Notes:
 * - Validation rules are usually enforced in the service/controller:
 *   - itemId must not be null
 *   - quantity must be > 0
 * - If the item already exists in the cart, service may increment quantity
 *   instead of creating a new cart line.
 */
public class AddToCartRequest {

    /**
     * The catalog item identifier to add to the cart.
     * (Represents a Product/Activity/etc. depending on your Item hierarchy)
     */
    private Long itemId;

    /**
     * Quantity to add.
     * Must be a positive integer (> 0).
     */
    private int quantity;

    // --- getters & setters ---

    public Long getItemId() { return itemId; }

    public void setItemId(Long itemId) { this.itemId = itemId; }

    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
}
