package com.build4all.tax.dto;

import java.math.BigDecimal;

/**
 * Optional response DTO if you want to avoid exposing
 * the TaxRule entity directly.
 *
 * Currently not wired in the controller, but you can easily
 * map TaxRule → TaxRuleResponse inside TaxService or TaxController.
 */
public class TaxRuleResponse {

    private Long id;
    private Long ownerProjectId;
    private String name;
    private BigDecimal rate;
    private boolean appliesToShipping;
    private Long countryId;
    private Long regionId;
    private boolean enabled;

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
}
