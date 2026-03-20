package com.build4all.order.service.impl;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.common.errors.ApiException;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.CheckoutLineSummary;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.order.service.CheckoutPricingService;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.promo.service.CouponService;
import com.build4all.shipping.dto.ShippingQuote;
import com.build4all.shipping.service.ShippingService;
import com.build4all.tax.service.TaxService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CheckoutPricingServiceImpl implements CheckoutPricingService {

    private final ShippingService shippingService;
    private final TaxService taxService;
    private final CurrencyRepository currencyRepository;
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

    @Override
    public CheckoutSummaryResponse priceCheckout(Long ownerProjectId,
                                                 Long currencyId,
                                                 CheckoutRequest request) {

        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        List<CartLine> lines = request.getLines();

        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        for (CartLine line : lines) {
            BigDecimal unit = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            int qty = line.getQuantity();
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty));

            line.setUnitPrice(unit);
            line.setLineSubtotal(lineTotal);

            itemsSubtotal = itemsSubtotal.add(lineTotal);
        }

        ShippingAddressDTO address = request.getShippingAddress();

        if (address != null && request.getShippingMethodId() != null) {
            address.setShippingMethodId(request.getShippingMethodId());
        }

        BigDecimal shippingTotal = BigDecimal.ZERO;

        if (address != null && address.getShippingMethodId() != null) {
            ShippingQuote quote = shippingService.getQuote(
                    ownerProjectId,
                    address,
                    lines
            );

            if (quote != null) {
                if (quote.getCost() != null) {
                    shippingTotal = quote.getCost();
                } else if (quote.getPrice() != null) {
                    shippingTotal = quote.getPrice();
                }
            }
        }

        if (shippingTotal.compareTo(BigDecimal.ZERO) < 0) {
            shippingTotal = BigDecimal.ZERO;
        }

        BigDecimal couponDiscount = BigDecimal.ZERO;
        String couponCode = null;
        String couponMessage = null;

        String requestedCouponCode = request.getCouponCode();

        if (requestedCouponCode != null && !requestedCouponCode.isBlank()) {
            try {
                Coupon coupon = couponService.validateForOrder(
                        ownerProjectId,
                        requestedCouponCode,
                        itemsSubtotal
                );

                if (coupon != null) {
                    if (coupon.getType() == CouponDiscountType.FREE_SHIPPING) {
                        shippingTotal = BigDecimal.ZERO;
                        couponDiscount = BigDecimal.ZERO;
                        couponCode = coupon.getCode();
                    } else {
                        BigDecimal computedDiscount =
                                couponService.computeDiscount(coupon, itemsSubtotal);

                        if (computedDiscount != null && computedDiscount.compareTo(BigDecimal.ZERO) > 0) {
                            couponDiscount = computedDiscount;
                            couponCode = coupon.getCode();
                        } else {
                            couponDiscount = BigDecimal.ZERO;
                            couponCode = null;
                            couponMessage = "Coupon was not applied because it did not affect this order";
                        }
                    }
                }
            } catch (Exception ex) {
                couponDiscount = BigDecimal.ZERO;
                couponCode = null;
                couponMessage = mapCouponErrorMessage(ex);
            }
        }

        if (shippingTotal.compareTo(BigDecimal.ZERO) < 0) {
            shippingTotal = BigDecimal.ZERO;
        }

        BigDecimal itemTaxTotal = taxService.calculateItemTax(
                ownerProjectId,
                address,
                lines
        );

        // IMPORTANT:
        // Calculate shipping tax ONLY after coupon/final shipping is known
        BigDecimal shippingTaxTotal = taxService.calculateShippingTax(
                ownerProjectId,
                address,
                shippingTotal
        );

        BigDecimal grandTotal = itemsSubtotal
                .add(shippingTotal)
                .add(itemTaxTotal)
                .add(shippingTaxTotal)
                .subtract(couponDiscount);

        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO;
        }

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

        CheckoutSummaryResponse response = new CheckoutSummaryResponse();
        response.setItemsSubtotal(itemsSubtotal);
        response.setShippingTotal(shippingTotal);
        response.setItemTaxTotal(itemTaxTotal);
        response.setShippingTaxTotal(shippingTaxTotal);
        response.setGrandTotal(grandTotal);
        response.setLines(lineSummaries);

        response.setCouponCode(couponCode);
        response.setCouponDiscount(couponDiscount);
        response.setMessage(couponMessage);

        if (currencyId != null) {
            Currency currency = currencyRepository.findById(currencyId).orElse(null);
            if (currency != null) {
                response.setCurrencyCode(currency.getCode());
                response.setCurrencySymbol(currency.getSymbol());
            }
        }

        return response;
    }

    private String mapCouponErrorMessage(Exception ex) {
        if (ex instanceof ApiException apiEx) {
            return switch (apiEx.getCode()) {
                case "COUPON_EXPIRED" -> "Coupon was not applied because it is expired";
                case "COUPON_USAGE_LIMIT_REACHED" -> "Coupon was not applied because it reached the usage limit";
                case "COUPON_INVALID" -> "Coupon was not applied because it is invalid";
                case "COUPON_MINIMUM_NOT_REACHED" -> "Coupon was not applied because order minimum was not reached";
                case "COUPON_INACTIVE" -> "Coupon was not applied because it is inactive";
                default -> "Coupon was not applied";
            };
        }

        String msg = ex == null || ex.getMessage() == null
                ? ""
                : ex.getMessage().trim().toLowerCase();

        if (msg.contains("expired")) {
            return "Coupon was not applied because it is expired";
        }
        if ((msg.contains("max") && msg.contains("use")) || msg.contains("usage limit")) {
            return "Coupon was not applied because it reached the usage limit";
        }
        if (msg.contains("inactive")) {
            return "Coupon was not applied because it is inactive";
        }
        if (msg.contains("not found") || msg.contains("invalid")) {
            return "Coupon was not applied because it is invalid";
        }
        if (msg.contains("minimum")) {
            return "Coupon was not applied because order minimum was not reached";
        }

        return "Coupon was not applied";
    }
}