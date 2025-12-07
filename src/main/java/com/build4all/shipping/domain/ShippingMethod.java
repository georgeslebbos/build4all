package com.build4all.shipping.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "shipping_methods")
public class ShippingMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_project_id", nullable = false)
    private AdminUserProject ownerProject;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 32)
    private ShippingMethodType type = ShippingMethodType.FLAT_RATE;

    @Column(name = "flat_rate", precision = 10, scale = 2)
    private BigDecimal flatRate = BigDecimal.ZERO;

    @Column(name = "price_per_kg", precision = 10, scale = 2)
    private BigDecimal pricePerKg = BigDecimal.ZERO;

    @Column(name = "free_shipping_threshold", precision = 10, scale = 2)
    private BigDecimal freeShippingThreshold;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ShippingMethodType getType() {
        return type;
    }

    public void setType(ShippingMethodType type) {
        this.type = type;
    }

    public BigDecimal getFlatRate() {
        return flatRate;
    }

    public void setFlatRate(BigDecimal flatRate) {
        this.flatRate = flatRate;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(BigDecimal pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public BigDecimal getFreeShippingThreshold() {
        return freeShippingThreshold;
    }

    public void setFreeShippingThreshold(BigDecimal freeShippingThreshold) {
        this.freeShippingThreshold = freeShippingThreshold;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
