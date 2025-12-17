package com.build4all.cart.dto;

/**
 * Request DTO used to update the quantity of an existing cart item.
 *
 * Typical usage:
 * - PUT /api/cart/items/{cartItemId}
 * - Body: { "quantity": 3 }
 *
 * Notes / behavior (as implemented in CartServiceImpl):
 * - If quantity > 0  -> update the cart item quantity
 * - If quantity <= 0 -> remove the item from the cart (delete the CartItem)
 *
 * Validation is usually handled in the service layer (or via @Min(0) / @Positive in future).
 */
public class UpdateCartItemRequest {

    /**
     * The new quantity for the cart item.
     * - quantity > 0  : keep item in cart and set this quantity
     * - quantity <= 0 : interpreted as "remove item from cart"
     */
    private int quantity;

    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
}
