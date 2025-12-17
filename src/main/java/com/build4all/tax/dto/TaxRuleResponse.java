package com.build4all.tax.dto;

import java.math.BigDecimal;

/**
 * Optional response DTO if you want to avoid exposing
 * the TaxRule entity directly.
 *
 * Why you may want this:
 * - Avoid sending JPA entities to the client (prevents lazy-loading JSON issues).
 * - Control exactly what fields are exposed.
 * - Keep API stable even if the entity changes.
 *
 * Typical usage:
 * - TaxController returns List<TaxRuleResponse> instead of List<TaxRule>
 * - Mapping happens in service/controller: TaxRule -> TaxRuleResponse
 *
 * Fields policy:
 * - We expose only IDs for relations (ownerProjectId, countryId, regionId),
 *   not the full Country/Region/AdminUserProject objects.
 */
public class TaxRuleResponse {

    /**
     * Database identifier of the rule.
     */
    private Long id;

    /**
     * Tenant/app identifier (AdminUserProject.id).
     * Allows frontend to know which ownerProject this rule belongs to.
     */
    private Long ownerProjectId;

    /**
     * Human-readable name (e.g., "Lebanon VAT", "Default Tax Rule").
     */
    private String name;

    /**
     * Tax percentage rate (e.g., 11.00 = 11%).
     */
    private BigDecimal rate;

    /**
     * If true, this rule can apply on shipping amount as well.
     */
    private boolean appliesToShipping;

    /**
     * Optional geographic filters:
     * If null => no filter for that dimension.
     */
    private Long countryId;
    private Long regionId;

    /**
     * Enable/disable flag.
     * Disabled rules are ignored by tax calculation.
     */
    private boolean enabled;

    // -------------------- getters --------------------

    public Long getId() {
        return id;
    }

    public Long getOwnerProjectId() {
        return ownerProjectId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public boolean isAppliesToShipping() {
        return appliesToShipping;
    }

    public Long getCountryId() {
        return countryId;
    }

    public Long getRegionId() {
        return regionId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // -------------------- setters --------------------

    public void setId(Long id) {
        this.id = id;
    }

    public void setOwnerProjectId(Long ownerProjectId) {
        this.ownerProjectId = ownerProjectId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public void setAppliesToShipping(boolean appliesToShipping) {
        this.appliesToShipping = appliesToShipping;
    }

    public void setCountryId(Long countryId) {
        this.countryId = countryId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /* -------------------- optional helper (recommended) --------------------
       If you like, add this static mapper to keep mapping consistent:

       public static TaxRuleResponse fromEntity(TaxRule rule) {
           TaxRuleResponse r = new TaxRuleResponse();
           r.setId(rule.getId());
           r.setOwnerProjectId(rule.getOwnerProject() != null ? rule.getOwnerProject().getId() : null);
           r.setName(rule.getName());
           r.setRate(rule.getRate());
           r.setAppliesToShipping(rule.isAppliesToShipping());
           r.setCountryId(rule.getCountry() != null ? rule.getCountry().getId() : null);
           r.setRegionId(rule.getRegion() != null ? rule.getRegion().getId() : null);
           r.setEnabled(rule.isEnabled());
           return r;
       }
     */
}
