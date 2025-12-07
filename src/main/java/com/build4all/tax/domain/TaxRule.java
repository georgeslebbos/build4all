package com.build4all.tax.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * TaxRule defines how tax is applied for a given ownerProject
 * (and optionally country/region and shipping).
 */
@Entity
@Table(name = "tax_rules")
public class TaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owner project that "owns" this set of tax rules.
    // We ignore it in JSON to avoid lazy loading/proxy issues.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_project_id", nullable = false)
    @JsonIgnore
    private AdminUserProject ownerProject;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal rate; // e.g., 10.00 = 10%

    @Column(name = "applies_to_shipping", nullable = false)
    private boolean appliesToShipping = false;

    // Optional geographic filters:
    // If you set these, the rule applies only for that country/region.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    @JsonIgnore
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    @JsonIgnore
    private Region region;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    // ===== Getters and Setters =====

    public Long getId() {
        return id;
    }

    public AdminUserProject getOwnerProject() {
        return ownerProject;
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

    public Country getCountry() {
        return country;
    }

    public Region getRegion() {
        return region;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOwnerProject(AdminUserProject ownerProject) {
        this.ownerProject = ownerProject;
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

    public void setCountry(Country country) {
        this.country = country;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
