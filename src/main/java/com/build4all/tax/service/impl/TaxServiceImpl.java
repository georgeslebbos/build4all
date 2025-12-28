package com.build4all.tax.service.impl;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.repository.TaxRuleRepository;
import com.build4all.tax.service.TaxService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
     * ✅ NEW (important):
     * Pick the best matching rule with clear priority:
     *   1) region match
     *   2) country match
     *   3) global rule (no country, no region)
     *
     * Also:
     * - If rule has country set but address.countryId is null -> NOT a match
     * - If rule has region set but address.regionId is null -> NOT a match
     *
     * If forShipping=true -> only rules where appliesToShipping=true.
     */
    private TaxRule pickBestRule(Long ownerProjectId,
                                 ShippingAddressDTO addr,
                                 boolean forShipping) {

        if (ownerProjectId == null) return null;

        List<TaxRule> rules = ruleRepository.findByOwnerProject_IdAndEnabledTrue(ownerProjectId);
        if (rules == null || rules.isEmpty()) return null;

        Long countryId = (addr != null) ? addr.getCountryId() : null;
        Long regionId  = (addr != null) ? addr.getRegionId()  : null;

        // 1) region-specific rule (strict match)
        TaxRule regionRule = rules.stream()
                .filter(r -> !forShipping || r.isAppliesToShipping())
                .filter(r -> r.getRegion() != null) // must be region-specific
                .filter(r -> regionId != null && Objects.equals(r.getRegion().getId(), regionId))
                .findFirst()
                .orElse(null);

        if (regionRule != null) return regionRule;

        // 2) country-specific rule (strict match)
        TaxRule countryRule = rules.stream()
                .filter(r -> !forShipping || r.isAppliesToShipping())
                .filter(r -> r.getRegion() == null) // region has priority, so ignore region rules here
                .filter(r -> r.getCountry() != null)
                .filter(r -> countryId != null && Objects.equals(r.getCountry().getId(), countryId))
                .findFirst()
                .orElse(null);

        if (countryRule != null) return countryRule;

        // 3) global rule (no country/region)
        return rules.stream()
                .filter(r -> !forShipping || r.isAppliesToShipping())
                .filter(r -> r.getCountry() == null && r.getRegion() == null)
                .findFirst()
                .orElse(null);
    }

    /* ==============================
       CRUD implementation
       ============================== */

    @Override
    public TaxRule createRule(TaxRule rule) {

        // tenant boundary: rule must belong to an ownerProject
        if (rule.getOwnerProject() == null || rule.getOwnerProject().getId() == null) {
            throw new IllegalArgumentException("ownerProject (with id) is required");
        }

        // basic validation
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        // rate is percentage (10.00 = 10%)
        if (rule.getRate() == null || rule.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("rate must be > 0");
        }

        return ruleRepository.save(rule);
    }

    /**
     * ✅ NEW:
     * Tenant-safe update: must provide ownerProjectId and we fetch by (id, ownerProjectId)
     * so nobody can update a rule from another tenant.
     */
    public TaxRule updateRuleScoped(Long ownerProjectId, Long id, TaxRule updates) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        TaxRule existing = ruleRepository.findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found for this ownerProject"));

        // Partial update (PATCH-like behavior)
        if (updates.getName() != null) existing.setName(updates.getName());

        if (updates.getRate() != null) {
            if (updates.getRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("rate must be > 0");
            }
            existing.setRate(updates.getRate());
        }

        // booleans: keep your behavior (always copy)
        existing.setAppliesToShipping(updates.isAppliesToShipping());
        existing.setEnabled(updates.isEnabled());

        // optional geographic filters
        existing.setCountry(updates.getCountry());
        existing.setRegion(updates.getRegion());

        // 🚫 DO NOT allow changing ownerProject here
        // existing.setOwnerProject(...);

        return ruleRepository.save(existing);
    }

    /**
     * Kept for interface compatibility.
     * Prefer calling updateRuleScoped from controller.
     */
    @Override
    public TaxRule updateRule(Long id, TaxRule updates) {
        throw new UnsupportedOperationException("Use updateRuleScoped(ownerProjectId, id, updates)");
    }

    /**
     * ✅ NEW:
     * Tenant-safe delete.
     */
    public void deleteRuleScoped(Long ownerProjectId, Long id) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        TaxRule rule = ruleRepository.findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found for this ownerProject"));

        // Choose hard delete or soft delete; you currently hard delete:
        ruleRepository.delete(rule);
    }

    /**
     * ✅ NEW:
     * Tenant-safe get.
     */
    public TaxRule getRuleScoped(Long ownerProjectId, Long id) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        return ruleRepository.findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found for this ownerProject"));
    }

    @Override
    public void deleteRule(Long id) {
        throw new UnsupportedOperationException("Use deleteRuleScoped(ownerProjectId, id)");
    }

    @Override
    public TaxRule getRule(Long id) {
        throw new UnsupportedOperationException("Use getRuleScoped(ownerProjectId, id)");
    }

    @Override
    public List<TaxRule> listRulesByOwnerProject(Long ownerProjectId) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        return ruleRepository.findByOwnerProject_Id(ownerProjectId);
    }

    /* ==============================
       Tax calculations
       ============================== */

    @Override
    public BigDecimal calculateItemTax(Long ownerProjectId,
                                       ShippingAddressDTO address,
                                       List<CartLine> lines) {

        if (lines == null || lines.isEmpty()) return BigDecimal.ZERO;

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : lines) {
            if (line == null) continue;
            BigDecimal unit = safe(line.getUnitPrice());
            int qty = line.getQuantity();
            subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(qty)));
        }

        TaxRule rule = pickBestRule(ownerProjectId, address, false);
        if (rule == null || rule.getRate() == null) return BigDecimal.ZERO;

        BigDecimal rate = rule.getRate();
        if (rate.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        // ✅ recommended rounding to 2 decimals (money). Adjust if your currency differs.
        return subtotal.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateShippingTax(Long ownerProjectId,
                                           ShippingAddressDTO address,
                                           BigDecimal shippingAmount) {

        BigDecimal shipping = safe(shippingAmount);
        if (shipping.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        TaxRule rule = pickBestRule(ownerProjectId, address, true);
        if (rule == null || rule.getRate() == null) return BigDecimal.ZERO;

        BigDecimal rate = rule.getRate();
        if (rate.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        return shipping.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
