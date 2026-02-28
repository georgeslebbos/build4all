package com.build4all.order.service;

import com.build4all.order.domain.OrderItem;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;

import jakarta.validation.Valid;

import java.util.List;

/**
 * OrderService
 *
 * The main domain service responsible for:
 * - Creating orders / order items
 * - Reading user/business order history
 * - Changing order status (cancel, refund, mark paid, etc.)
 * - Running the unified checkout flow (ecommerce + activities)
 *
 * Notes about the design you currently have:
 * 1) You have "legacy" flows:
 *    - createBookItem(...) : Activity booking with Stripe PaymentIntent ID coming from client.
 *    - createCashorderByBusiness(...) : Business-side cash creation for activities.
 *
 * 2) You also have the new generic checkout:
 *    - checkout(...) : Creates Order + OrderItems from cart lines and then (in the new version)
 *      starts payment via PaymentOrchestratorService.
 *
 * In practice:
 * - The controller endpoint: POST /api/orders/checkout calls checkout(userId, request).
 * - OrderServiceImpl performs:
 *   a) validation + load items + capacity checks
 *   b) pricing via CheckoutPricingService (shipping/tax/coupon)
 *   c) persist Order header + OrderItem lines
 *   d) start payment (Stripe/Cash/PayPal...) and return payment info in CheckoutSummaryResponse
 *   e) clear cart (after payment start succeeds)
 */
public interface OrderService {

    /**
     * Legacy activity booking: "pay first then create order".
     *
     * - The client already has stripePaymentId (PaymentIntent id),
     *   typically after confirming payment on the client.
     * - The server validates Stripe PaymentIntent status (succeeded),
     *   then creates Order + a single OrderItem.
     *
     * @param userId          user performing the booking
     * @param itemId          activity item id
     * @param quantity        participants/seats
     * @param stripePaymentId Stripe PaymentIntent id (pi_...)
     * @param currencyId      optional currency id for the order
     * @return created OrderItem (line)
     */
    OrderItem createBookItem(Long userId, Long itemId, int quantity,
                             String stripePaymentId, Long currencyId);

    /**
     * Legacy cash creation: created by business on behalf of a user (or business user).
     *
     * - Used when payment is offline (cash).
     * - "wasPaid" indicates whether the cash was collected immediately or still pending.
     *
     * @param itemId          activity item id
     * @param businessUserId  the user id created/selected by business in the flow
     * @param quantity        participants/seats
     * @param wasPaid         cash collected or not
     * @param currencyId      optional currency id
     * @return created OrderItem (line)
     */
    OrderItem createCashorderByBusiness(Long itemId, Long businessUserId,
                                        int quantity, boolean wasPaid, Long currencyId);

    /**
     * Quick gate: does the user already have an order for a given item?
     * Often used to prevent double booking/purchase or to allow access to content.
     *
     * @param itemId item id
     * @param userId user id
     * @return true if an order item exists for this user + item
     */
    boolean hasUserAlreadyBooked(Long itemId, Long userId);

    /**
     * List all order items for a user (history).
     * Usually sorted by createdAt desc in the repository.
     */
    List<OrderItem> getMyorders(Long userId);

    /**
     * List all order items for a user filtered by order header status name
     * (e.g., PENDING / COMPLETED / CANCELED / ...).
     */
    List<OrderItem> getMyordersByStatus(Long userId, String status);

    /* ===============================
       STATUS MUTATIONS (user actions)
       =============================== */

    /**
     * Cancel an order item (typically by user).
     * Implementation should enforce "cannot cancel completed".
     */
    void cancelorder(Long orderItemId, Long actorId);

    /**
     * Reset an order back to PENDING (typically by user; depends on your business rules).
     */
    void resetToPending(Long orderItemId, Long actorId);

    /**
     * Delete an order item record (dangerous; make sure business rules allow it).
     * Implementation currently tries to allow owner or fallback lookup.
     */
    void deleteorder(Long orderItemId, Long actorId);

    /* ===============================
       REFUND / CANCEL WORKFLOW
       =============================== */

    /**
     * Refund if eligible according to current status rules.
     * In your implementation:
     * - CANCELED -> REFUNDED
     * - PENDING/CANCEL_REQUESTED -> CANCELED then REFUNDED
     * - COMPLETED -> forbidden
     */
    void refundIfEligible(Long orderItemId, Long actorId);

    /** User requests cancel (moves to CANCEL_REQUESTED). */
    void requestCancel(Long orderItemId, Long userId);

    /** Business approves cancel (moves to CANCELED). */
    void approveCancel(Long orderItemId, Long businessId);

    /** Business rejects cancel (moves back to PENDING). */
    void rejectCancel(Long orderItemId, Long businessId);

    /** Business marks refunded (moves to REFUNDED). */
    void markRefunded(Long orderItemId, Long businessId);

    /** Business rejects order (moves to REJECTED). */
    void rejectorder(Long orderItemId, Long businessId);

    /** Business un-rejects order (moves back to PENDING). */
    void unrejectorder(Long orderItemId, Long businessId);

    /* ===============================
       BUSINESS VIEWS / ACTIONS
       =============================== */

    /**
     * List all order items belonging to a business (scoped via item.business).
     */
    List<OrderItem> getordersByBusiness(Long businessId);

    /**
     * Mark order as paid/completed (business side).
     * In your implementation: sets header status to COMPLETED.
     *
     * Note: In the new payment flow, this should ideally be driven by payment webhook confirmation
     * (Stripe webhook -> markPaid), not manually, except for CASH.
     */
    void markPaid(Long orderItemId, Long businessId);

    /**
     * Admin/maintenance method to delete all order items for an item (e.g. when item removed).
     */
    void deleteordersByItemId(Long itemId);

    /* ===============================
       NEW UNIFIED CHECKOUT
       =============================== */

    /**
     * Unified checkout for ecommerce + activities.
     *
     * New recommended flow:
     * 1) Validate cart lines, currency, payment method
     * 2) Load items, check stock/capacity
     * 3) Price totals using CheckoutPricingService (shipping + tax + coupon)
     * 4) Create Order header + OrderItems
     * 5) Start payment using PaymentOrchestratorService (Stripe/Cash/PayPal...)
     * 6) Return CheckoutSummaryResponse including payment data (clientSecret/redirectUrl/etc.)
     * 7) Clear cart after payment initiation succeeds
     *
     * @param userId  authenticated user
     * @param request checkout request payload
     * @return priced summary + orderId/orderDate (+ payment info in the new version)
     */
    CheckoutSummaryResponse checkout(Long userId, CheckoutRequest request);

	CheckoutSummaryResponse checkoutFromCart(Long userId, @Valid CheckoutRequest request);
	
	CheckoutSummaryResponse quoteCheckoutFromCart(Long userId, CheckoutRequest request);

	void failCashOrder(Long orderItemId, Long businessId, String reason);
}
