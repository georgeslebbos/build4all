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
     * ✅ Tenant-scoped update (security + correctness).
     */
    Coupon update(Long ownerProjectId, Long id, CouponRequest req);

    /**
     * ✅ Tenant-scoped delete.
     */
    void delete(Long ownerProjectId, Long id);

    /**
     * ✅ Tenant-scoped get.
     */
    Coupon get(Long ownerProjectId, Long id);

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

    /**
     * Keep mapping centralized (optional but cleaner than repeating in controller).
     */
    CouponResponse toResponse(Coupon c);
}
