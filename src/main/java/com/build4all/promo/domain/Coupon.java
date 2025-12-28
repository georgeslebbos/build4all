package com.build4all.promo.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "coupons",
        uniqueConstraints = {
                /**
                 * ✅ Multi-tenant uniqueness:
                 * Code must be unique PER owner_project_id (per app),
                 * not globally across the whole database.
                 *
                 * This fixes the common SaaS bug where coupon codes conflict between apps.
                 */
                @UniqueConstraint(
                        name = "uk_coupon_owner_code",
                        columnNames = {"owner_project_id", "code"}
                )
        }
)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant/app scope.
     * We keep it as Long (not ManyToOne) for simplicity in promo module.
     *
     * IMPORTANT:
     * Because this is just a Long, you must ALWAYS scope queries by ownerProjectId
     * to prevent cross-tenant access (security issue).
     */
    @Column(name = "owner_project_id", nullable = false)
    private Long ownerProjectId;

    /**
     * Coupon code.
     *
     * ✅ IMPORTANT CHANGE:
     * Remove unique=true because that would enforce global uniqueness across all tenants.
     * The correct uniqueness is now enforced by the composite unique constraint above.
     */
    @Column(nullable = false, length = 100)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private CouponDiscountType type; // PERCENT, FIXED, FREE_SHIPPING

    @Column(name = "value", precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "global_usage_limit")
    private Integer globalUsageLimit;

    @Column(name = "used_count")
    private Integer usedCount = 0;

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // ===== Getters & Setters =====

    public Long getId() {
        return id;
    }

    public Long getOwnerProjectId() {
        return ownerProjectId;
    }

    public void setOwnerProjectId(Long ownerProjectId) {
        this.ownerProjectId = ownerProjectId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CouponDiscountType getType() {
        return type;
    }

    public void setType(CouponDiscountType type) {
        this.type = type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Integer getGlobalUsageLimit() {
        return globalUsageLimit;
    }

    public void setGlobalUsageLimit(Integer globalUsageLimit) {
        this.globalUsageLimit = globalUsageLimit;
    }

    public Integer getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(Integer usedCount) {
        this.usedCount = usedCount;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
