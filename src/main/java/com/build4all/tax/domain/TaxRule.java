package com.build4all.tax.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_rules")
public class TaxRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_project_id", nullable = false)
    private AdminUserProject ownerProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "rate_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal ratePercent;

    @Column(name = "applies_to_shipping", nullable = false)
    private boolean appliesToShipping = false;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public Long getId() {
        return id;
    }

    public AdminUserProject getOwnerProject() {
        return ownerProject;
    }

    public void setOwnerProject(AdminUserProject ownerProject) {
        this.ownerProject = ownerProject;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public void setRatePercent(BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
    }

    public boolean isAppliesToShipping() {
        return appliesToShipping;
    }

    public void setAppliesToShipping(boolean appliesToShipping) {
        this.appliesToShipping = appliesToShipping;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
