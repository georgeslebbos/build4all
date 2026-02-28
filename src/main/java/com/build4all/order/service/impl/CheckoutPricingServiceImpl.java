package com.build4all.order.service.impl;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.CheckoutLineSummary;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.order.service.CheckoutPricingService;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.service.CouponService;
import com.build4all.shipping.dto.ShippingQuote;
import com.build4all.shipping.service.ShippingService;
import com.build4all.tax.service.TaxService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CheckoutPricingServiceImpl
 *
 * This class is the "pricing engine" for checkout.
 * It does NOT create orders and does NOT trigger payment.
 *
 * Responsibilities:
 * 1) Compute items subtotal from cart lines (unitPrice * quantity)
 * 2) Compute shipping total using ShippingService (if address + shipping method exist)
 * 3) Compute item tax and shipping tax using TaxService
 * 4) Validate coupon and compute discount using CouponService
 * 5) Return a CheckoutSummaryResponse to be used by OrderServiceImpl
 *
 * Notes:
 * - Multi-tenant: pricing logic depends on ownerProjectId (each app can have its own shipping/tax/coupon rules).
 * - The CartLine.unitPrice is usually filled earlier in OrderServiceImpl (from Item/Product effective price).
 */
@Service
@Transactional
public class CheckoutPricingServiceImpl implements CheckoutPricingService {

    /** Calculates shipping quotes (cost) based on address + lines + ownerProject rules */
    private final ShippingService shippingService;

    /** Calculates taxes for items and shipping based on country/region/app rules */
    private final TaxService taxService;

    /** Loads currency info (code + symbol) to include in response for UI */
    private final CurrencyRepository currencyRepository;

    /** Validates coupons and computes the discount amount */
    private final CouponService couponService;

    public CheckoutPricingServiceImpl(ShippingService shippingService,
                                      TaxService taxService,
                                      CurrencyRepository currencyRepository,
                                      CouponService couponService) {
        this.shippingService = shippingService;
        this.taxService = taxService;
        this.currencyRepository = currencyRepository;
        this.couponService = couponService;
    }

    /**
     * Calculate checkout totals for the given cart request.
     *
     * @param ownerProjectId identifies the app/tenant (shipping/tax/coupon rules can vary per app)
     * @param currencyId     optional currency id (used to fill currencyCode/currencySymbol)
     * @param request        cart lines + shipping info + coupon info
     * @return CheckoutSummaryResponse (items subtotal, shipping, taxes, discount, grand total, line summaries)
     */
    @Override
    public CheckoutSummaryResponse priceCheckout(Long ownerProjectId,
                                                 Long currencyId,
                                                 CheckoutRequest request) {

        // Guard: must have at least 1 line to price
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Cart lines (each line should have itemId, quantity, and ideally unitPrice)
        List<CartLine> lines = request.getLines();

        // ---- Compute items subtotal and enrich lines ----
        // itemsSubtotal = Σ(unitPrice * quantity)
        // Also re-set unitPrice/lineSubtotal to ensure no null values.
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        for (CartLine line : lines) {
            BigDecimal unit = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            int qty = line.getQuantity();
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty));

            // Keep the line enriched for consumers (controllers/UI/logging)
            line.setUnitPrice(unit);
            line.setLineSubtotal(lineTotal);

            itemsSubtotal = itemsSubtotal.add(lineTotal);
        }

        // ---- Shipping & tax ----
        // Shipping depends on address + selected shipping method.
        ShippingAddressDTO address = request.getShippingAddress();

        // Keep your previous behavior: copy selected shippingMethodId into address
        // (Some clients send shippingMethodId outside the address object.)
        if (address != null && request.getShippingMethodId() != null) {
            address.setShippingMethodId(request.getShippingMethodId());
        }

        BigDecimal shippingTotal = BigDecimal.ZERO;

        // Calculate shipping only if the user selected a shipping method
        if (address != null && address.getShippingMethodId() != null) {
            // Shipping service can return a quote based on:
            // - ownerProjectId (tenant rules)
            // - destination address (country/region/city)
            // - cart lines (weight/size/categories/etc. if you implement these rules)
            ShippingQuote quote = shippingService.getQuote(
                    ownerProjectId,
                    address,
                    lines
            );

            // If quote is available, take its cost
            if (quote != null && quote.getCost() != null) {
                shippingTotal = quote.getCost();
            }
        }

        // Tax on items: usually based on item categories + destination address + app rules
        BigDecimal itemTaxTotal = taxService.calculateItemTax(
                ownerProjectId,
                address,
                lines
        );

        // Tax on shipping: some regions tax shipping cost
        BigDecimal shippingTaxTotal = taxService.calculateShippingTax(
                ownerProjectId,
                address,
                shippingTotal
        );

        // ---- Coupon logic ----
        // Coupon discount currently applies on itemsSubtotal (not shipping/tax).
        BigDecimal couponDiscount = BigDecimal.ZERO;
        String couponCode = request.getCouponCode();

        if (couponCode != null && !couponCode.isBlank()) {
            // Validate coupon constraints:
            // - exists and active
            // - belongs to ownerProjectId
            // - minimum subtotal, expiry, usage limits, etc.
            Coupon coupon = couponService.validateForOrder(
                    ownerProjectId,
                    couponCode,
                    itemsSubtotal
            );

            // If valid, compute discount amount (fixed or percentage)
            if (coupon != null) {
                couponDiscount = couponService.computeDiscount(coupon, itemsSubtotal);
            }
        }

        // ---- Grand total (after discount) ----
        // grand = items + shipping + itemTax + shippingTax - discount
        BigDecimal grandTotal = itemsSubtotal
                .add(shippingTotal)
                .add(itemTaxTotal)
                .add(shippingTaxTotal)
                .subtract(couponDiscount);

        // Safety: avoid negative totals if discount > totals
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO;
        }

        // ---- Build line summaries ----
        // These line summaries are returned to the client/UI.
        // itemName is null here because this class focuses only on pricing.
        List<CheckoutLineSummary> lineSummaries = lines.stream()
        		.map(line -> new CheckoutLineSummary(
        		        line.getItemId(),
        		        (line.getItemName() == null || line.getItemName().trim().isBlank())
        		            ? null
        		            : line.getItemName().trim(),
        		        line.getQuantity(),
        		        line.getUnitPrice(),
        		        line.getLineSubtotal()
        		))
                .collect(Collectors.toList());

        // ---- Build response ----
        // This response will be used by OrderServiceImpl to:
        // - save totals into Order header
        // - show breakdown to the user before/while paying
        CheckoutSummaryResponse response = new CheckoutSummaryResponse();
        response.setItemsSubtotal(itemsSubtotal);
        response.setShippingTotal(shippingTotal);
        response.setItemTaxTotal(itemTaxTotal);
        response.setShippingTaxTotal(shippingTaxTotal);
        response.setGrandTotal(grandTotal);
        response.setLines(lineSummaries);

        // NEW: coupon info in response
        response.setCouponCode(couponCode);
        response.setCouponDiscount(couponDiscount);

        // Currency info (optional; used by UI to display totals)
        if (currencyId != null) {
            Currency currency = currencyRepository.findById(currencyId).orElse(null);
            if (currency != null) {
                response.setCurrencyCode(currency.getCode());
                response.setCurrencySymbol(currency.getSymbol());
            }
        }

        return response;
    }
}
