package com.build4all.tax.dto;

import java.math.BigDecimal;

/**
 * Request DTO for creating/updating tax rules.
 * Sent from the frontend / Postman.
 */
public class TaxRuleRequest {

    private Long ownerProjectId;   // required
    private String name;           // required
    private BigDecimal rate;       // required, > 0 (e.g. 11.00 for 11%)
    private boolean appliesToShipping;
    private Long countryId;        // optional
    private Long regionId;         // optional
    private boolean enabled = true;

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
