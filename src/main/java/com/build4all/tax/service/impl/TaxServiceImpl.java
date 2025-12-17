package com.build4all.tax.service.impl;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.repository.TaxRuleRepository;
import com.build4all.tax.service.TaxService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime; // (currently unused) keep if you plan to timestamp rules later
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

    /**
     * Null-safe BigDecimal helper.
     * Any null numeric value becomes 0 to avoid NullPointerException in calculations.
     */
    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Pick the first enabled rule that matches country/region (if any).
     * If forShipping = true, we only consider rules with appliesToShipping = true.
     *
     * Important:
     * - This is a "first match wins" strategy. If you later need prioritization
     *   (region rule > country rule > global rule), you can sort rules before filtering.
     * - Current behavior only filters when BOTH the rule has country/region AND the address provides it.
     *   If address doesn't provide country/region, the rule will pass (treated as "matches").
     */
    private TaxRule pickRule(Long ownerProjectId,
                             ShippingAddressDTO addr,
                             boolean forShipping) {

        // tenant/app is required to select the correct tax rules set
        if (ownerProjectId == null) {
            return null;
        }

        // Uses ManyToOne ownerProject relation
        // We only load enabled rules for calculations
        List<TaxRule> rules = ruleRepository
                .findByOwnerProject_IdAndEnabledTrue(ownerProjectId);

        if (rules == null || rules.isEmpty()) {
            return null;
        }

        // Extract address filters (can be null if shipping not provided)
        Long countryId = (addr != null) ? addr.getCountryId() : null;
        Long regionId  = (addr != null) ? addr.getRegionId()  : null;

        return rules.stream()
                // if calculating shipping tax, only rules that apply to shipping are eligible
                .filter(r -> !forShipping || r.isAppliesToShipping())
                .filter(r -> {
                    // country filter:
                    // - if rule.country is set AND address.countryId is set, they must match
                    // - otherwise we don't block the rule here
                    if (r.getCountry() != null && countryId != null) {
                        if (!Objects.equals(r.getCountry().getId(), countryId)) {
                            return false;
                        }
                    }
                    // region filter:
                    // - if rule.region is set AND address.regionId is set, they must match
                    // - otherwise we don't block the rule here
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

        // tenant boundary: rule must belong to an ownerProject
        if (rule.getOwnerProject() == null ||
                rule.getOwnerProject().getId() == null) {
            throw new IllegalArgumentException("ownerProject (with id) is required");
        }

        // basic validation
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        // rate is percentage (10.00 = 10%)
        if (rule.getRate() == null ||
                rule.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("rate must be > 0");
        }

        // Enabled default
        if (!rule.isEnabled()) {
            // keep whatever client sends; you can force true if you like
        }

        // Persist rule
        return ruleRepository.save(rule);
    }

    @Override
    public TaxRule updateRule(Long id, TaxRule updates) {

        // Load existing rule (or fail)
        TaxRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found"));

        // Partial update (PATCH-like behavior)
        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getRate() != null) {
            if (updates.getRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("rate must be > 0");
            }
            existing.setRate(updates.getRate());
        }

        // boolean fields: always copied (because boolean has a default value)
        existing.setAppliesToShipping(updates.isAppliesToShipping());

        // optional geographic filters
        if (updates.getCountry() != null) {
            existing.setCountry(updates.getCountry());
        }
        if (updates.getRegion() != null) {
            existing.setRegion(updates.getRegion());
        }

        // enable/disable rule
        existing.setEnabled(updates.isEnabled());

        // we usually do not allow changing ownerProject here,
        // but if you want, you can copy it as well.

        return ruleRepository.save(existing);
    }

    @Override
    public void deleteRule(Long id) {
        // Validate existence to return a clean error message
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
        // Admin listing: returns all rules (enabled + disabled)
        return ruleRepository.findByOwnerProject_Id(ownerProjectId);
    }

    /* ==============================
       TaxService calculation methods
       ============================== */

    @Override
    public BigDecimal calculateItemTax(Long ownerProjectId,
                                       ShippingAddressDTO address,
                                       List<CartLine> lines) {

        // No lines => no tax
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // subtotal of all items
        // (we compute from unitPrice * quantity to avoid relying on lineSubtotal consistency)
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

        // rate is percentage, e.g. 11.00 means 11%
        BigDecimal rate = rule.getRate();
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // subtotal * (rate/100)
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

        // shipping * (rate/100)
        return shipping.multiply(rate)
                .divide(BigDecimal.valueOf(100));
    }
}
