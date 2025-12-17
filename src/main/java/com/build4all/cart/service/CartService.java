package com.build4all.cart.service;

import com.build4all.cart.dto.AddToCartRequest;
import com.build4all.cart.dto.CartResponse;
import com.build4all.cart.dto.UpdateCartItemRequest;

/**
 * CartService
 *
 * Business-facing contract for everything related to a user's shopping cart.
 * Typical flow:
 * 1) User views cart           -> getMyCart()
 * 2) User adds items           -> addToCart()
 * 3) User updates quantities   -> updateCartItem()
 * 4) User removes items        -> removeCartItem()
 * 5) User clears cart          -> clearCart()
 * 6) User checks out           -> checkout()  (cart -> order)
 *
 * Notes:
 * - All methods are scoped by userId to guarantee ownership/security.
 * - Implementations usually load the ACTIVE cart (or create one if missing).
 * - Returned CartResponse is a DTO meant for the frontend (Flutter) and should be a stable API shape.
 */
public interface CartService {

    /**
     * Fetch the currently ACTIVE cart for the user (with its items, prices, currency, totals).
     *
     * Expected behavior:
     * - If no active cart exists, many implementations create one with status ACTIVE and return it.
     * - Items should include enough details for UI (item id/name/image, quantity, unitPrice, subtotal, ...).
     *
     * @param userId authenticated user's id
     * @return cart DTO representing the active cart
     */
    CartResponse getMyCart(Long userId);

    /**
     * Add an item to the user's ACTIVE cart.
     *
     * Expected behavior:
     * - If item already exists in cart: increment quantity (or replace, depending on business rules).
     * - Capture "unitPrice at time of adding" (important if prices can change later).
     * - Recalculate cart totals after modification.
     *
     * @param userId authenticated user's id
     * @param request contains itemId, quantity and any extra fields needed (variationId, notes, etc.)
     * @return updated cart DTO
     */
    CartResponse addToCart(Long userId, AddToCartRequest request);

    /**
     * Update a specific cart item (usually quantity).
     *
     * Ownership rules:
     * - cartItemId must belong to the user's ACTIVE cart.
     *
     * Expected behavior:
     * - Validate quantity > 0 (or treat 0 as remove).
     * - Recalculate totals.
     *
     * @param userId authenticated user's id
     * @param cartItemId id of CartItem row
     * @param request update payload (typically quantity)
     * @return updated cart DTO
     */
    CartResponse updateCartItem(Long userId, Long cartItemId, UpdateCartItemRequest request);

    /**
     * Remove a cart item from the user's ACTIVE cart.
     *
     * Expected behavior:
     * - Validate ownership (cart item belongs to user's cart).
     * - Remove item and recalculate totals.
     *
     * @param userId authenticated user's id
     * @param cartItemId id of CartItem row
     * @return updated cart DTO
     */
    CartResponse removeCartItem(Long userId, Long cartItemId);

    /**
     * Clear all items from the user's ACTIVE cart.
     *
     * Typical implementation:
     * - Bulk delete cart items (deleteByCart_Id)
     * - cart.getItems().clear()
     * - set totals to zero
     *
     * @param userId authenticated user's id
     */
    void clearCart(Long userId);

    // converts active cart to Order + OrderItems, returns created orderId
    /**
     * Checkout operation: converts the user's ACTIVE cart into an Order + OrderItems.
     *
     * ⚠️ Important note (based on your code evolution):
     * - OLD flow (legacy): pay first (stripePaymentId) -> then create Order.
     * - NEW flow (recommended): create Order -> start payment -> return clientSecret/redirectUrl.
     *
     * This method signature still reflects the OLD flow (stripePaymentId + couponCode on checkout call).
     * If you already migrated to OrderService.checkout(...) + PaymentOrchestrator,
     * you may later replace/extend this signature to return a CheckoutSummaryResponse instead of only orderId.
     *
     * Expected behavior:
     * - Validate cart is not empty
     * - Resolve currencyId
     * - Apply couponCode if provided
     * - Persist Order header + lines
     * - Mark cart as CONVERTED / clear items
     *
     * @param userId authenticated user's id
     * @param paymentMethod payment method code (e.g., "STRIPE", "CASH")
     * @param stripePaymentId legacy Stripe payment intent id (pi_...) when using old flow; may be null in new flow
     * @param currencyId currency to use for the order (required for pricing + display)
     * @param couponCode optional coupon code to discount items subtotal
     * @return newly created order id
     */
    Long checkout(Long userId,
                  String paymentMethod,
                  String stripePaymentId,
                  Long currencyId,
                  String couponCode);
}
