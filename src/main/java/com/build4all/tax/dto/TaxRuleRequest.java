package com.build4all.tax.dto;

import java.math.BigDecimal;

/**
 * Request DTO for creating/updating tax rules.
 *
 * Why this DTO exists:
 * - We don’t want the frontend to send a full TaxRule entity (with JPA relations).
 * - We accept simple primitives/IDs (ownerProjectId, countryId, regionId),
 *   then the service/controller resolves them to entities.
 *
 * Typical usage:
 * - POST /api/tax-rules   -> create a rule
 * - PUT  /api/tax-rules/{id} -> update a rule
 *
 * Notes:
 * - rate is a percentage value (11.00 means 11%).
 * - appliesToShipping controls whether this rule can be used to tax shipping fees.
 * - countryId/regionId are optional geographic filters (if provided).
 */
public class TaxRuleRequest {

    /**
     * Tenant/app scope.
     * Required because Build4All is multi-tenant:
     * every tax rule belongs to exactly one ownerProject (AdminUserProject).
     */
    private Long ownerProjectId;   // required

    /**
     * Human readable rule name (e.g., "Lebanon VAT", "KSA VAT", "Default Tax").
     * Required.
     */
    private String name;           // required

    /**
     * Tax percentage rate.
     * Required and must be > 0 (e.g. 11.00 for 11%).
     *
     * IMPORTANT:
     * This is NOT a fraction (0.11). It's "11.00".
     */
    private BigDecimal rate;       // required, > 0 (e.g. 11.00 for 11%)

    /**
     * If true, this rule is allowed to apply on shipping fees
     * (used by TaxService.calculateShippingTax()).
     *
     * If false, shipping tax should return 0 even if rate exists.
     */
    private boolean appliesToShipping;

    /**
     * Optional filter: apply this tax rule only for a specific country.
     * If null -> the rule is country-agnostic (global for that ownerProject).
     */
    private Long countryId;        // optional

    /**
     * Optional filter: apply this tax rule only for a specific region within the country.
     * If null -> no region filtering.
     */
    private Long regionId;         // optional

    /**
     * Enable/disable the rule.
     * Default is true so newly created rules are active unless explicitly disabled.
     */
    private boolean enabled = true;

    // -------------------- getters & setters --------------------

    public Long getOwnerProjectId() {
        return ownerProjectId;
    }

    public void setOwnerProjectId(Long ownerProjectId) {
        this.ownerProjectId = ownerProjectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public boolean isAppliesToShipping() {
        return appliesToShipping;
    }

    public void setAppliesToShipping(boolean appliesToShipping) {
        this.appliesToShipping = appliesToShipping;
    }

    public Long getCountryId() {
        return countryId;
    }

    public void setCountryId(Long countryId) {
        this.countryId = countryId;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
