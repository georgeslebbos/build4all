package com.build4all.tax.service.impl;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.repository.TaxRuleRepository;
import com.build4all.tax.service.TaxService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class TaxServiceImpl implements TaxService {

    private final TaxRuleRepository ruleRepository;

    public TaxServiceImpl(TaxRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public BigDecimal calculateItemTax(Long ownerProjectId,
                                       ShippingAddressDTO address,
                                       List<CartLine> items) {

        if (ownerProjectId == null || items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : items) {
            BigDecimal unit = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            int qty = line.getQuantity();
            subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(qty)));
        }

        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = resolveEffectiveRate(ownerProjectId, address, false);
        return subtotal.multiply(rate).divide(BigDecimal.valueOf(100));
    }

    @Override
    public BigDecimal calculateShippingTax(Long ownerProjectId,
                                           ShippingAddressDTO address,
                                           BigDecimal shippingTotal) {

        if (ownerProjectId == null || shippingTotal == null ||
                shippingTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = resolveEffectiveRate(ownerProjectId, address, true);
        return shippingTotal.multiply(rate).divide(BigDecimal.valueOf(100));
    }

    private BigDecimal resolveEffectiveRate(Long ownerProjectId,
                                            ShippingAddressDTO address,
                                            boolean forShipping) {

        List<TaxRule> rules =
                ruleRepository.findByOwnerProjectIdAndEnabledTrue(ownerProjectId);

        BigDecimal totalRate = BigDecimal.ZERO;

        for (TaxRule rule : rules) {
            if (rule.isAppliesToShipping() != forShipping) {
                continue;
            }

            if (!matchesAddress(rule, address)) {
                continue;
            }

            BigDecimal r = rule.getRatePercent();
            if (r != null) {
                totalRate = totalRate.add(r);
            }
        }

        return totalRate;
    }

    private boolean matchesAddress(TaxRule rule, ShippingAddressDTO address) {
        if (address == null) return true;

        if (rule.getCountry() != null && address.getCountryId() != null) {
            if (!rule.getCountry().getId().equals(address.getCountryId())) {
                return false;
            }
        }

        if (rule.getRegion() != null && address.getRegionId() != null) {
            if (!rule.getRegion().getId().equals(address.getRegionId())) {
                return false;
            }
        }

        return true;
    }
}
