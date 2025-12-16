package com.build4all.order.dto;

/**
 * CashOrderRequest
 *
 * DTO used when a business/owner records an OFFLINE (cash) order manually.
 * Typical use case (legacy flow):
 * - Business staff sells/records a booking or product purchase paid in cash
 * - Backend creates an Order + OrderItem without any online payment gateway
 *
 * Field meaning:
 * - itemId: the item being purchased/booked (Activity or Product, etc.)
 * - businessUserId: the user id being used as the buyer/customer in this legacy endpoint
 *   (naming is a bit confusing: it is the "customer user id" saved on Order.user)
 * - quantity: number of units / seats / pieces to order
 * - wasPaid: indicates whether cash was already collected at the counter
 *   (often used to decide if order should later be marked COMPLETED)
 * - currencyId: optional currency reference (if null, system may default by ownerProject / platform)
 *
 * Notes:
 * - This request is separate from the new checkout flow (/api/orders/checkout),
 *   where payment is started via PaymentOrchestratorService.
 * - Consider renaming businessUserId -> userId or customerUserId later for clarity.
 */
public class CashOrderRequest {

    /** Item/Product/Activity id being ordered */
    private Long itemId;

    /**
     * Legacy: the customer user id (stored as Order.user).
     * (Name kept for backward compatibility with your older endpoints.)
     */
    private Long businessUserId;

    /** Quantity to order */
    private int quantity;

    /**
     * If true, cash is already collected (order can be marked paid later).
     * If false, cash is expected later (order stays pending).
     */
    private boolean wasPaid;

    /** Optional: currency id to attach to the order (null allowed) */
    private Long currencyId;  // optional

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getBusinessUserId() { return businessUserId; }
    public void setBusinessUserId(Long businessUserId) { this.businessUserId = businessUserId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** JavaBean boolean getter uses "isXxx" */
    public boolean isWasPaid() { return wasPaid; }
    public void setWasPaid(boolean wasPaid) { this.wasPaid = wasPaid; }

    public Long getCurrencyId() { return currencyId; }
    public void setCurrencyId(Long currencyId) { this.currencyId = currencyId; }
}
