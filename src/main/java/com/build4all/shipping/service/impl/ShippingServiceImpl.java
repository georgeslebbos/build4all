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
                BigDecimal qty = BigDecimal.valueOf(line.getQuantity());
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

    private boolean matchesAddress(ShippingMethod method, ShippingAddressDTO addr) {
        if (method == null) return false;

        Long methodCountryId = method.getCountry() != null ? method.getCountry().getId() : null;
        Long methodRegionId = method.getRegion() != null ? method.getRegion().getId() : null;

        Long addrCountryId = addr != null ? addr.getCountryId() : null;
        Long addrRegionId = addr != null ? addr.getRegionId() : null;

        // If method is restricted by country, address must match it
        if (methodCountryId != null) {
            if (addrCountryId == null || !methodCountryId.equals(addrCountryId)) {
                return false;
            }
        }

        // If method is restricted by region, address must match it
        if (methodRegionId != null) {
            if (addrRegionId == null || !methodRegionId.equals(addrRegionId)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Decide if this method should appear at checkout.
     * Filter first, then calculate.
     */
    private boolean isEligible(ShippingMethod method,
                               ShippingAddressDTO addr,
                               BigDecimal itemsSubtotal,
                               BigDecimal totalWeight) {

        if (method == null || !method.isEnabled()) return false;

        if (!matchesAddress(method, addr)) {
            return false;
        }

        ShippingMethodType type = method.getType();
        if (type == null) type = ShippingMethodType.FLAT_RATE;

        switch (type) {
            case FREE:
            case LOCAL_PICKUP:
            case FLAT_RATE:
            case PRICE_BASED:
                return true;

            case WEIGHT_BASED:
            case PRICE_PER_KG:
                return totalWeight.compareTo(BigDecimal.ZERO) > 0;

            case FREE_OVER_THRESHOLD:
                BigDecimal threshold = method.getFreeShippingThreshold();
                return threshold != null && itemsSubtotal.compareTo(threshold) >= 0;

            default:
                return true;
        }
    }

    /**
     * Core pricing logic after eligibility passed.
     */
    private BigDecimal computePrice(ShippingMethod method,
                                    BigDecimal itemsSubtotal,
                                    BigDecimal totalWeight) {

        if (method == null) return BigDecimal.ZERO;

        ShippingMethodType type = method.getType();
        if (type == null) type = ShippingMethodType.FLAT_RATE;

        BigDecimal flat = safe(method.getFlatRate());
        BigDecimal perKg = safe(method.getPricePerKg());
        BigDecimal threshold = method.getFreeShippingThreshold();

        switch (type) {
            case FREE:
                return BigDecimal.ZERO;

            case LOCAL_PICKUP:
                return BigDecimal.ZERO;

            case FLAT_RATE:
                return flat;

            case WEIGHT_BASED:
            case PRICE_PER_KG:
                return perKg.multiply(safe(totalWeight));

            case PRICE_BASED:
                return flat;

            case FREE_OVER_THRESHOLD:
                if (threshold != null && itemsSubtotal.compareTo(threshold) >= 0) {
                    return BigDecimal.ZERO;
                }
                // Not eligible logically, but safety fallback:
                return BigDecimal.ZERO;

            default:
                return flat;
        }
    }

    private List<ShippingMethod> findEligibleMethods(Long ownerProjectId,
                                                     ShippingAddressDTO addr,
                                                     List<CartLine> cartLines) {
        List<ShippingMethod> methods =
                methodRepository.findByOwnerProject_IdAndEnabledTrue(ownerProjectId);

        if (methods == null || methods.isEmpty()) {
            return List.of();
        }

        BigDecimal itemsSubtotal = sumItemsSubtotal(cartLines);
        BigDecimal totalWeight = sumTotalWeight(cartLines);

        List<ShippingMethod> eligible = new ArrayList<>();
        for (ShippingMethod method : methods) {
            if (isEligible(method, addr, itemsSubtotal, totalWeight)) {
                eligible.add(method);
            }
        }

        return eligible;
    }

    /* ========================= main API ========================= */

    @Override
    public ShippingQuote getQuote(Long ownerProjectId,
                                  ShippingAddressDTO addr,
                                  List<CartLine> cartLines) {

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required for shipping quote");
        }

        List<ShippingMethod> eligibleMethods = findEligibleMethods(ownerProjectId, addr, cartLines);

        if (eligibleMethods.isEmpty()) {
            return new ShippingQuote(
                    null,
                    "No shipping",
                    BigDecimal.ZERO,
                    null
            );
        }

        BigDecimal itemsSubtotal = sumItemsSubtotal(cartLines);
        BigDecimal totalWeight = sumTotalWeight(cartLines);

        ShippingMethod chosen = null;
        Long requestedMethodId = (addr != null) ? addr.getShippingMethodId() : null;

        if (requestedMethodId != null) {
            for (ShippingMethod m : eligibleMethods) {
                if (m.getId().equals(requestedMethodId)) {
                    chosen = m;
                    break;
                }
            }

            if (chosen == null) {
                throw new IllegalArgumentException("Selected shipping method is not available for this address/cart");
            }
        }

        if (chosen == null) {
            chosen = eligibleMethods.get(0);
        }

        BigDecimal price = computePrice(chosen, itemsSubtotal, totalWeight);

        return new ShippingQuote(
                chosen.getId(),
                chosen.getName(),
                price,
                null
        );
    }

    @Override
    public List<ShippingQuote> getAvailableMethods(Long ownerProjectId,
                                                   ShippingAddressDTO addr,
                                                   List<CartLine> cartLines) {

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required for shipping methods");
        }

        List<ShippingMethod> eligibleMethods = findEligibleMethods(ownerProjectId, addr, cartLines);

        if (eligibleMethods.isEmpty()) {
            return List.of();
        }

        BigDecimal itemsSubtotal = sumItemsSubtotal(cartLines);
        BigDecimal totalWeight = sumTotalWeight(cartLines);

        List<ShippingQuote> quotes = new ArrayList<>();
        for (ShippingMethod method : eligibleMethods) {
            BigDecimal price = computePrice(method, itemsSubtotal, totalWeight);
            quotes.add(new ShippingQuote(
                    method.getId(),
                    method.getName(),
                    price,
                    null
            ));
        }

        return quotes;
    }
}