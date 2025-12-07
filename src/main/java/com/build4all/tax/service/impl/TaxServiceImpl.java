package com.build4all.tax.service.impl;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.repository.TaxRuleRepository;
import com.build4all.tax.service.TaxService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class TaxServiceImpl implements TaxService {

    private final TaxRuleRepository ruleRepository;

    public TaxServiceImpl(TaxRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /* ==============================
       Helpers
       ============================== */

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Pick the first enabled rule that matches country/region (if any).
     * If forShipping = true, we only consider rules with appliesToShipping = true.
     */
    private TaxRule pickRule(Long ownerProjectId,
                             ShippingAddressDTO addr,
                             boolean forShipping) {

        if (ownerProjectId == null) {
            return null;
        }

        // Uses ManyToOne ownerProject relation
        List<TaxRule> rules = ruleRepository
                .findByOwnerProject_IdAndEnabledTrue(ownerProjectId);

        if (rules == null || rules.isEmpty()) {
            return null;
        }

        Long countryId = (addr != null) ? addr.getCountryId() : null;
        Long regionId  = (addr != null) ? addr.getRegionId()  : null;

        return rules.stream()
                .filter(r -> !forShipping || r.isAppliesToShipping())
                .filter(r -> {
                    // country filter
                    if (r.getCountry() != null && countryId != null) {
                        if (!Objects.equals(r.getCountry().getId(), countryId)) {
                            return false;
                        }
                    }
                    // region filter
                    if (r.getRegion() != null && regionId != null) {
                        if (!Objects.equals(r.getRegion().getId(), regionId)) {
                            return false;
                        }
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);
    }

    /* ==============================
       CRUD implementation
       ============================== */

    @Override
    public TaxRule createRule(TaxRule rule) {

        if (rule.getOwnerProject() == null ||
                rule.getOwnerProject().getId() == null) {
            throw new IllegalArgumentException("ownerProject (with id) is required");
        }
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (rule.getRate() == null ||
                rule.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("rate must be > 0");
        }

        // Enabled default
        if (!rule.isEnabled()) {
            // keep whatever client sends; you can force true if you like
        }

        return ruleRepository.save(rule);
    }

    @Override
    public TaxRule updateRule(Long id, TaxRule updates) {

        TaxRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found"));

        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getRate() != null) {
            if (updates.getRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("rate must be > 0");
            }
            existing.setRate(updates.getRate());
        }

        existing.setAppliesToShipping(updates.isAppliesToShipping());

        if (updates.getCountry() != null) {
            existing.setCountry(updates.getCountry());
        }
        if (updates.getRegion() != null) {
            existing.setRegion(updates.getRegion());
        }

        existing.setEnabled(updates.isEnabled());

        // we usually do not allow changing ownerProject here,
        // but if you want, you can copy it as well.

        return ruleRepository.save(existing);
    }

    @Override
    public void deleteRule(Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new IllegalArgumentException("TaxRule not found");
        }
        ruleRepository.deleteById(id);
    }

    @Override
    public TaxRule getRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found"));
    }

    @Override
    public List<TaxRule> listRulesByOwnerProject(Long ownerProjectId) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        return ruleRepository.findByOwnerProject_Id(ownerProjectId);
    }

    /* ==============================
       TaxService calculation methods
       ============================== */

    @Override
    public BigDecimal calculateItemTax(Long ownerProjectId,
                                       ShippingAddressDTO address,
                                       List<CartLine> lines) {

        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // subtotal of all items
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : lines) {
            BigDecimal unit = safe(line.getUnitPrice());
            int qty = line.getQuantity();
            subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(qty)));
        }

        // Pick rule (not shipping-specific)
        TaxRule rule = pickRule(ownerProjectId, address, false);
        if (rule == null || rule.getRate() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = rule.getRate(); // e.g., 11.00
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return subtotal.multiply(rate)
                .divide(BigDecimal.valueOf(100));
    }

    @Override
    public BigDecimal calculateShippingTax(Long ownerProjectId,
                                           ShippingAddressDTO address,
                                           BigDecimal shippingAmount) {

        BigDecimal shipping = safe(shippingAmount);
        if (shipping.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Pick rule that applies to shipping
        TaxRule rule = pickRule(ownerProjectId, address, true);
        if (rule == null || rule.getRate() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = rule.getRate();
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return shipping.multiply(rate)
                .divide(BigDecimal.valueOf(100));
    }
}
