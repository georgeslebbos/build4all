package com.build4all.promo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponRequest {

    private Long ownerProjectId;
    private String code;
    private String description;

    // Must match JSON: "discountType" (PERCENT / FIXED / FREE_SHIPPING)
    private String discountType;

    // Must match JSON: "discountValue"
    private BigDecimal discountValue;

    // Global usage limit (JSON: maxUses)
    private Integer maxUses;

    // Per-user limit (optional – not yet enforced)
    private Integer perUserLimit;

    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;

    // JSON: startsAt / expiresAt
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;

    /**
     * ✅ IMPORTANT:
     * Make it nullable to avoid the "default false" problem:
     * - if frontend doesn't send active, boolean defaults to false
     * - coupon gets disabled accidentally.
     *
     * Strategy:
     * - CREATE: if null => default true
     * - UPDATE: if null => do not change existing value
     */
    private Boolean active;

    public Long getOwnerProjectId() {
        return ownerProjectId;
    }

    public void setOwnerProjectId(Long ownerProjectId) {
        this.ownerProjectId = ownerProjectId;
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

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
