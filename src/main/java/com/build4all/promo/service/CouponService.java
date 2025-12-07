package com.build4all.promo.service;

import com.build4all.promo.domain.Coupon;
import com.build4all.promo.dto.CouponRequest;
import com.build4all.promo.dto.CouponResponse;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    /**
     * Create a new coupon from the given request DTO.
     */
    CouponResponse create(CouponRequest req);

    /**
     * Update an existing coupon by ID using the given partial Coupon entity as updates.
     */
    Coupon update(Long id, Coupon coupon);

    /**
     * Delete coupon by ID.
     */
    void delete(Long id);

    /**
     * Get a coupon by ID or throw IllegalArgumentException if not found.
     */
    Coupon get(Long id);

    /**
     * List all coupons for a given ownerProjectId (one app).
     */
    List<Coupon> listByOwnerProject(Long ownerProjectId);

    /**
     * Validate a coupon for a given ownerProject + itemsSubtotal.
     * Returns the coupon if valid, or null if not valid.
     * Throws IllegalArgumentException for "hard" validation errors (e.g. not found).
     */
    Coupon validateForOrder(Long ownerProjectId, String code, BigDecimal itemsSubtotal);

    /**
     * Compute the discount amount for this coupon on the given itemsSubtotal.
     * (Currently discount is applied on itemsSubtotal only.)
     */
    BigDecimal computeDiscount(Coupon coupon, BigDecimal itemsSubtotal);
}
