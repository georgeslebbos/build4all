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
 *
 * Conceptually:
 * - Each tenant/app (AdminUserProject / ownerProject) can define multiple tax rules.
 * - A rule can be global (no country/region), country-specific, or region-specific.
 * - A rule can apply to items only OR to shipping as well.
 *
 * Typical usage (TaxService):
 * 1) Determine shipping address (country/region) from CheckoutRequest
 * 2) Load enabled rules for ownerProject, then pick the best match:
 *    - region match wins over country match
 *    - country match wins over global rule
 * 3) Apply rate (%) to:
 *    - items subtotal  (always if rule is selected)
 *    - shipping total  (only if appliesToShipping = true)
 */
@Entity
@Table(name = "tax_rules")
public class TaxRule {

    /** Primary key for this tax rule. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owner project that "owns" this set of tax rules.
    // We ignore it in JSON to avoid lazy loading/proxy issues.
    // (Also avoids circular serialization when returning TaxRule objects.)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_project_id", nullable = false)
    @JsonIgnore
    private AdminUserProject ownerProject;

    /** Human readable name shown in admin UI, ex: "VAT Lebanon", "KSA VAT 15%", "No Tax". */
    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Tax rate as percentage value.
     * Example:
     * - 10.00 means 10% (NOT 0.10)
     *
     * precision=5, scale=2 → max value 999.99 (more than enough for tax %).
     */
    @Column(name = "rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal rate; // e.g., 10.00 = 10%

    /**
     * If true: this same tax rule is also applied on shipping cost.
     * If false: shipping tax is 0 (or calculated by another rule depending on your TaxService design).
     */
    @Column(name = "applies_to_shipping", nullable = false)
    private boolean appliesToShipping = false;

    // Optional geographic filters:
    // If you set these, the rule applies only for that country/region.
    //
    // Note:
    // - Both are LAZY and @JsonIgnore to avoid loading them during serialization.
    // - When applying rules, your TaxService usually compares by ID (countryId/regionId).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    @JsonIgnore
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    @JsonIgnore
    private Region region;

    /**
     * Soft enable/disable flag for admin.
     * We keep records for history but exclude disabled rules from calculations.
     */
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
