package com.build4all.order.service.impl;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.CheckoutLineSummary;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.order.service.CheckoutPricingService;
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

    public CheckoutPricingServiceImpl(ShippingService shippingService,
                                      TaxService taxService,
                                      CurrencyRepository currencyRepository) {
        this.shippingService = shippingService;
        this.taxService = taxService;
        this.currencyRepository = currencyRepository;
    }

    @Override
    public CheckoutSummaryResponse priceCheckout(Long ownerProjectId,
                                                 Long currencyId,
                                                 CheckoutRequest request) {

        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        List<CartLine> lines = request.getLines();

        // ---- Compute items subtotal and enrich lines ----
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        for (CartLine line : lines) {
            BigDecimal unit = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            int qty = line.getQuantity();
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty));

            line.setUnitPrice(unit);
            line.setLineSubtotal(lineTotal);

            itemsSubtotal = itemsSubtotal.add(lineTotal);
        }

        // ---- Shipping & tax ----
        ShippingAddressDTO address = request.getShippingAddress();

        // Make sure the selected shipping method is present on the address DTO
        if (address != null && request.getShippingMethodId() != null) {
            address.setShippingMethodId(request.getShippingMethodId());
        }

        BigDecimal shippingTotal = BigDecimal.ZERO;

        if (address != null && address.getShippingMethodId() != null) {
            // ShippingService.getQuote(ownerProjectId, address, lines)
            ShippingQuote quote = shippingService.getQuote(
                    ownerProjectId,
                    address,
                    lines
            );
            if (quote != null && quote.getCost() != null) {
                shippingTotal = quote.getCost();
            }
        }

        BigDecimal itemTaxTotal = taxService.calculateItemTax(
                ownerProjectId,
                address,
                lines
        );

        BigDecimal shippingTaxTotal = taxService.calculateShippingTax(
                ownerProjectId,
                address,
                shippingTotal
        );

        BigDecimal grandTotal = itemsSubtotal
                .add(shippingTotal)
                .add(itemTaxTotal)
                .add(shippingTaxTotal);

        // ---- Build line summaries ----
        List<CheckoutLineSummary> lineSummaries = lines.stream()
                .map(line -> new CheckoutLineSummary(
                        line.getItemId(),
                        null, // itemName can be filled on client or another layer
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getLineSubtotal()
                ))
                .collect(Collectors.toList());

        // ---- Build response ----
        CheckoutSummaryResponse response = new CheckoutSummaryResponse();
        response.setItemsSubtotal(itemsSubtotal);
        response.setShippingTotal(shippingTotal);
        response.setItemTaxTotal(itemTaxTotal);
        response.setShippingTaxTotal(shippingTaxTotal);
        response.setGrandTotal(grandTotal);
        response.setLines(lineSummaries);

        if (currencyId != null) {
            Currency currency = currencyRepository.findById(currencyId).orElse(null);
            if (currency != null) {
                response.setCurrencySymbol(currency.getSymbol());
            }
        }

        return response;
    }
}
