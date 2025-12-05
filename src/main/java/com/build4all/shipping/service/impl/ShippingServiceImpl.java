package com.build4all.shipping.service.impl;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.shipping.domain.ShippingMethod;
import com.build4all.shipping.domain.ShippingMethodType;
import com.build4all.shipping.dto.ShippingQuote;
import com.build4all.shipping.repository.ShippingMethodRepository;
import com.build4all.shipping.service.ShippingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShippingServiceImpl implements ShippingService {

    private final ShippingMethodRepository methodRepository;

    public ShippingServiceImpl(ShippingMethodRepository methodRepository) {
        this.methodRepository = methodRepository;
    }

    /* ========================= helpers ========================= */

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal sumItemsSubtotal(List<CartLine> lines) {
        if (lines == null || lines.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : lines) {
            if (line == null) continue;

            BigDecimal lineSubtotal = line.getLineSubtotal();
            if (lineSubtotal != null) {
                total = total.add(lineSubtotal);
            } else if (line.getUnitPrice() != null) {
                BigDecimal unit = safe(line.getUnitPrice());
                BigDecimal qty  = BigDecimal.valueOf(line.getQuantity());
                total = total.add(unit.multiply(qty));
            }
        }
        return total;
    }

    private BigDecimal sumTotalWeight(List<CartLine> lines) {
        if (lines == null || lines.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : lines) {
            if (line == null) continue;
            BigDecimal w = line.getWeightKg();
            if (w != null) {
                total = total.add(w);
            }
        }
        return total;
    }

    /**
     * Core pricing logic based on ShippingMethodType.
     */
    private BigDecimal computePrice(ShippingMethod method,
                                    BigDecimal itemsSubtotal,
                                    BigDecimal totalWeight) {

        if (method == null) return BigDecimal.ZERO;

        ShippingMethodType type = method.getType();
        if (type == null) type = ShippingMethodType.FLAT_RATE;

        BigDecimal flat       = safe(method.getFlatRate());
        BigDecimal perKg      = safe(method.getPricePerKg());
        BigDecimal threshold  = method.getFreeShippingThreshold();

        switch (type) {
            case FREE:
                // Always free
                return BigDecimal.ZERO;

            case LOCAL_PICKUP:
                // Customer picks up → no shipping cost
                return BigDecimal.ZERO;

            case FLAT_RATE:
                // Fixed cost
                return flat;

            case WEIGHT_BASED:
            case PRICE_PER_KG:
                // Cost based solely on weight
                return perKg.multiply(safe(totalWeight));

            case PRICE_BASED:
                // For now: same as flat rate.
                // You can change later to: flat + (itemsSubtotal * percentage) etc.
                return flat;

            case FREE_OVER_THRESHOLD:
                // If subtotal >= threshold → free
                if (threshold != null && itemsSubtotal.compareTo(threshold) >= 0) {
                    return BigDecimal.ZERO;
                }
                // Otherwise: fallback to flat, then per-kg, then 0
                if (flat.compareTo(BigDecimal.ZERO) > 0) {
                    return flat;
                }
                if (perKg.compareTo(BigDecimal.ZERO) > 0) {
                    return perKg.multiply(safe(totalWeight));
                }
                return BigDecimal.ZERO;

            default:
                // Safety net
                return flat;
        }
    }

    /* ========================= main API ========================= */

    @Override
    public ShippingQuote getQuote(Long ownerProjectId,
                                  ShippingAddressDTO addr,
                                  List<CartLine> cartLines) {

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required for shipping quote");
        }

        // 1) Load enabled methods for that app
        List<ShippingMethod> methods =
                methodRepository.findByOwnerProject_IdAndEnabledTrue(ownerProjectId);

        if (methods == null || methods.isEmpty()) {
            // No methods configured → 0 cost
            return new ShippingQuote(
                    null,
                    "No shipping",
                    BigDecimal.ZERO,
                    null
            );
        }

        // 2) Compute totals for pricing logic
        BigDecimal itemsSubtotal = sumItemsSubtotal(cartLines);
        BigDecimal totalWeight   = sumTotalWeight(cartLines);

        // 3) Choose a method:
        //    - if addr contains shippingMethodId, prefer that
        //    - else fall back to the first enabled method
        ShippingMethod chosen = null;
        Long requestedMethodId = (addr != null) ? addr.getShippingMethodId() : null;

        if (requestedMethodId != null) {
            for (ShippingMethod m : methods) {
                if (m.getId().equals(requestedMethodId)) {
                    chosen = m;
                    break;
                }
            }
        }

        if (chosen == null) {
            chosen = methods.get(0);
        }

        // 4) Compute final shipping price
        BigDecimal price = computePrice(chosen, itemsSubtotal, totalWeight);

        // (Optional) You can inject currency symbol from elsewhere.
        String currencySymbol = null;

        return new ShippingQuote(
                chosen.getId(),
                chosen.getName(),
                price,
                currencySymbol
        );
    }

    @Override
    public List<ShippingQuote> getAvailableMethods(Long ownerProjectId,
                                                   ShippingAddressDTO addr,
                                                   List<CartLine> cartLines) {

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required for shipping methods");
        }

        List<ShippingMethod> methods =
                methodRepository.findByOwnerProject_IdAndEnabledTrue(ownerProjectId);

        if (methods == null || methods.isEmpty()) {
            return List.of();
        }

        BigDecimal itemsSubtotal = sumItemsSubtotal(cartLines);
        BigDecimal totalWeight   = sumTotalWeight(cartLines);

        List<ShippingQuote> quotes = new ArrayList<>();
        for (ShippingMethod method : methods) {
            BigDecimal price = computePrice(method, itemsSubtotal, totalWeight);
            quotes.add(new ShippingQuote(
                    method.getId(),
                    method.getName(),
                    price,
                    null  // currency symbol (optional)
            ));
        }
        return quotes;
    }
}
