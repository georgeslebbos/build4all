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

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MAX_TAX_RATE = new BigDecimal("100.00");

    public TaxServiceImpl(TaxRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /* ==============================
       Helpers
       ============================== */

    /**
     * Null-safe BigDecimal helper.
     */
    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void validateRuleForSave(TaxRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Tax rule is required");
        }

        if (rule.getOwnerProject() == null || rule.getOwnerProject().getId() == null) {
            throw new IllegalArgumentException("ownerProject (with id) is required");
        }

        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        if (rule.getRate() == null) {
            throw new IllegalArgumentException("rate is required");
        }

        if (rule.getRate().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("rate must be greater than 0");
        }

        if (rule.getRate().compareTo(MAX_TAX_RATE) > 0) {
            throw new IllegalArgumentException("rate cannot be greater than 100%");
        }
    }

    /**
     * Scope priority model:
     * - REGION:{id}
     * - COUNTRY:{id}
     * - GLOBAL
     */
    private String scopeKey(TaxRule rule) {
        if (rule == null) return "UNKNOWN";

        if (rule.getRegion() != null && rule.getRegion().getId() != null) {
            return "REGION:" + rule.getRegion().getId();
        }

        if (rule.getCountry() != null && rule.getCountry().getId() != null) {
            return "COUNTRY:" + rule.getCountry().getId();
        }

        return "GLOBAL";
    }

    private boolean sameScope(TaxRule a, TaxRule b) {
        return Objects.equals(scopeKey(a), scopeKey(b));
    }

    private String humanScopeLabel(TaxRule rule) {
        if (rule == null) return "scope";

        if (rule.getRegion() != null && rule.getRegion().getId() != null) {
            return "region";
        }

        if (rule.getCountry() != null && rule.getCountry().getId() != null) {
            return "country";
        }

        return "global";
    }

    /**
     * one enabled rule per scope only
     */
    private void validateNoEnabledConflict(TaxRule candidate, Long currentIdOrNull) {
        if (candidate == null) return;

        // only enforce overlap check when the candidate is enabled
        if (!candidate.isEnabled()) return;

        Long ownerProjectId = candidate.getOwnerProject() != null
                ? candidate.getOwnerProject().getId()
                : null;

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        List<TaxRule> enabledRules = ruleRepository.findByOwnerProject_IdAndEnabledTrue(ownerProjectId);

        for (TaxRule existing : enabledRules) {
            if (existing == null || existing.getId() == null) continue;

            if (currentIdOrNull != null && existing.getId().equals(currentIdOrNull)) {
                continue;
            }

            if (sameScope(candidate, existing)) {
                throw new IllegalArgumentException(
                        "Only one enabled tax rule is allowed for the same " + humanScopeLabel(candidate)
                );
            }
        }
    }

    /**
     * Pick the best matching rule with clear priority:
     *   1) region match
     *   2) country match
     *   3) global rule
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
        Long regionId = (addr != null) ? addr.getRegionId() : null;

        // 1) region-specific rule
        TaxRule regionRule = rules.stream()
                .filter(r -> !forShipping || r.isAppliesToShipping())
                .filter(r -> r.getRegion() != null)
                .filter(r -> regionId != null && Objects.equals(r.getRegion().getId(), regionId))
                .findFirst()
                .orElse(null);

        if (regionRule != null) return regionRule;

        // 2) country-specific rule
        TaxRule countryRule = rules.stream()
                .filter(r -> !forShipping || r.isAppliesToShipping())
                .filter(r -> r.getRegion() == null)
                .filter(r -> r.getCountry() != null)
                .filter(r -> countryId != null && Objects.equals(r.getCountry().getId(), countryId))
                .findFirst()
                .orElse(null);

        if (countryRule != null) return countryRule;

        // 3) global rule
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
        validateRuleForSave(rule);
        validateNoEnabledConflict(rule, null);
        return ruleRepository.save(rule);
    }

    /**
     * Tenant-safe update
     */
    public TaxRule updateRuleScoped(Long ownerProjectId, Long id, TaxRule updates) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        TaxRule existing = ruleRepository.findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found for this ownerProject"));

        if (updates.getName() != null) {
            if (updates.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            existing.setName(updates.getName());
        }

        if (updates.getRate() != null) {
            if (updates.getRate().compareTo(ZERO) <= 0) {
                throw new IllegalArgumentException("rate must be greater than 0");
            }
            if (updates.getRate().compareTo(MAX_TAX_RATE) > 0) {
                throw new IllegalArgumentException("rate cannot be greater than 100%");
            }
            existing.setRate(updates.getRate());
        }

        existing.setAppliesToShipping(updates.isAppliesToShipping());
        existing.setEnabled(updates.isEnabled());
        existing.setCountry(updates.getCountry());
        existing.setRegion(updates.getRegion());

        validateRuleForSave(existing);
        validateNoEnabledConflict(existing, existing.getId());

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
     * Tenant-safe delete
     */
    public void deleteRuleScoped(Long ownerProjectId, Long id) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        TaxRule rule = ruleRepository.findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("TaxRule not found for this ownerProject"));

        ruleRepository.delete(rule);
    }

    /**
     * Tenant-safe get
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