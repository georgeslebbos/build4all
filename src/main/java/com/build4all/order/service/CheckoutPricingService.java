package com.build4all.order.service;

import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;

/**
 * CheckoutPricingService
 *
 * A dedicated "pricing engine" used during checkout.
 *
 * Goal:
 * - Compute ALL money-related totals for the checkout in ONE place, consistently.
 *
 * What it typically calculates:
 * - Items subtotal (sum of each line: unitPrice * quantity)
 * - Shipping total (based on shipping address + chosen shipping method + cart lines)
 * - Item tax total (tax applied on items, depends on address and tax rules)
 * - Shipping tax total (tax applied on shipping cost, depends on tax rules)
 * - Coupon discount (if couponCode is present and valid)
 * - Grand total = subtotal + shipping + taxes - discount
 *
 * Why we keep pricing separate from OrderServiceImpl:
 * - Single responsibility: OrderServiceImpl focuses on creating Order/OrderItem + starting payment.
 * - Reusability: can be used for:
 *   - "preview/quote" endpoint (show totals before placing order)
 *   - "reprice" during shipping method change
 *   - future admin recalculation / invoicing
 * - Consistency: ensures totals used for payment = totals stored on Order.
 *
 * Important input expectations (based on your current implementation):
 * - ownerProjectId:
 *   - Needed to load tenant-specific rules (shipping rules, tax rules, coupons, etc.)
 * - currencyId:
 *   - Used to set response currency code/symbol (and later for conversion if you add it)
 * - request:
 *   - Contains cart lines (itemId + quantity) and optional:
 *     shippingAddress, shippingMethodId, couponCode
 *   - In your flow, OrderServiceImpl enriches each CartLine with unitPrice and lineSubtotal
 *     BEFORE calling this pricing service, so this service can compute totals without reloading items.
 *
 * Output:
 * - CheckoutSummaryResponse:
 *   - Contains computed totals + per-line summaries used by the client UI
 *   - OrderServiceImpl later adds orderId/orderDate (after saving the Order)
 */
public interface CheckoutPricingService {

    /**
     * Price the checkout request for a specific app (ownerProjectId) and currency.
     *
     * @param ownerProjectId tenant/app identifier (all cart items must belong to this app)
     * @param currencyId     currency of the checkout (used to fill currencyCode/currencySymbol)
     * @param request        checkout payload containing cart lines + shipping/coupon inputs
     * @return fully-priced checkout summary (totals + line summaries)
     */
    CheckoutSummaryResponse priceCheckout(Long ownerProjectId,
                                          Long currencyId,
                                          CheckoutRequest request);
}
