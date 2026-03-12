package com.build4all.promo.service.impl;

import com.build4all.common.errors.ApiException;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.promo.dto.CouponRequest;
import com.build4all.promo.dto.CouponResponse;
import com.build4all.promo.repository.CouponRepository;
import com.build4all.promo.service.CouponService;
import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    public CouponServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /* ============================
       CREATE
       ============================ */

    @Override
    public CouponResponse create(CouponRequest req) {

        if (req.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (req.getDiscountType() == null || req.getDiscountType().isBlank()) {
            throw new IllegalArgumentException("discountType is required");
        }

        CouponDiscountType type =
                CouponDiscountType.valueOf(req.getDiscountType().toUpperCase());

        // ✅ Validation depends on type:
        // - FREE_SHIPPING: allow value null/0
        // - others: must be > 0
        BigDecimal value = req.getDiscountValue();
        if (type != CouponDiscountType.FREE_SHIPPING) {
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("discountValue must be > 0");
            }
        } else {
            value = BigDecimal.ZERO;
        }

        // ✅ Per-tenant uniqueness check (better error than DB constraint)
        couponRepository.findByOwnerProjectIdAndCodeIgnoreCase(req.getOwnerProjectId(), req.getCode().trim())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Coupon code already exists for this ownerProjectId");
                });
        
        LocalDateTime from = req.getStartsAt();
        LocalDateTime to = req.getExpiresAt();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("startsAt must be <= expiresAt");
        }

        Coupon coupon = new Coupon();
        coupon.setOwnerProjectId(req.getOwnerProjectId());
        coupon.setCode(req.getCode().trim());
        coupon.setDescription(req.getDescription());
        coupon.setType(type);
        coupon.setValue(value);
        coupon.setGlobalUsageLimit(req.getMaxUses());
        coupon.setMinOrderAmount(req.getMinOrderAmount());
        if (type == CouponDiscountType.PERCENT) {
            coupon.setMaxDiscountAmount(req.getMaxDiscountAmount());
        } else {
            coupon.setMaxDiscountAmount(null);
        }
        coupon.setValidFrom(req.getStartsAt());
        coupon.setValidTo(req.getExpiresAt());

        // ✅ Default active=true if not provided
        coupon.setActive(req.getActive() == null ? true : req.getActive());

        coupon.setUsedCount(0);

        coupon = couponRepository.save(coupon);

        return toResponse(coupon);
    }
    
    
    @Override
    public void consumeOrThrow(Long ownerProjectId, String code) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("coupon code is required");

        int updated = couponRepository.consumeIfAvailable(ownerProjectId, code.trim());
        if (updated != 1) {
            throw new IllegalArgumentException("Coupon max uses reached (or coupon inactive)");
        }
    }

    @Override
    public void releaseOne(Long ownerProjectId, String code) {
        if (ownerProjectId == null) return;
        if (code == null || code.isBlank()) return;
        couponRepository.releaseOne(ownerProjectId, code.trim());
    }

    /* ============================
       UPDATE / DELETE / GET / LIST
       ============================ */

    @Override
    public Coupon update(Long ownerProjectId, Long id, CouponRequest req) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        // ✅ tenant-scoped load
        Coupon existing = couponRepository.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found for this ownerProjectId"));

        if (req.getCode() != null && !req.getCode().isBlank()) {
            String newCode = req.getCode().trim();

            // prevent duplicates in same tenant
            couponRepository.findByOwnerProjectIdAndCodeIgnoreCase(ownerProjectId, newCode)
                    .ifPresent(other -> {
                        if (!other.getId().equals(existing.getId())) {
                            throw new IllegalArgumentException("Coupon code already exists for this ownerProjectId");
                        }
                    });

            existing.setCode(newCode);
        }

        if (req.getDescription() != null) existing.setDescription(req.getDescription());

        if (req.getDiscountType() != null && !req.getDiscountType().isBlank()) {
            CouponDiscountType type = CouponDiscountType.valueOf(req.getDiscountType().toUpperCase());
            existing.setType(type);

            // If switching to FREE_SHIPPING, force value to 0
            if (type == CouponDiscountType.FREE_SHIPPING) {
                existing.setValue(BigDecimal.ZERO);
            }
        }

        if (req.getDiscountValue() != null) {
            // For FREE_SHIPPING, ignore any value and force 0
            if (existing.getType() == CouponDiscountType.FREE_SHIPPING) {
                existing.setValue(BigDecimal.ZERO);
            } else {
                if (req.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("discountValue must be > 0");
                }
                existing.setValue(req.getDiscountValue());
            }
        }
        
        LocalDateTime newFrom = req.getStartsAt() != null ? req.getStartsAt() : existing.getValidFrom();
        LocalDateTime newTo = req.getExpiresAt() != null ? req.getExpiresAt() : existing.getValidTo();

        if (newFrom != null && newTo != null && newFrom.isAfter(newTo)) {
            throw new IllegalArgumentException("startsAt must be <= expiresAt");
        }

        if (req.getMaxUses() != null) existing.setGlobalUsageLimit(req.getMaxUses());
        if (req.getMinOrderAmount() != null) existing.setMinOrderAmount(req.getMinOrderAmount());
        if (existing.getType() == CouponDiscountType.PERCENT) {
            if (req.getMaxDiscountAmount() != null) {
                existing.setMaxDiscountAmount(req.getMaxDiscountAmount());
            }
        } else {
            // ✅ For FIXED / FREE_SHIPPING ignore max discount
            existing.setMaxDiscountAmount(null);
        }
        if (req.getStartsAt() != null) existing.setValidFrom(req.getStartsAt());
        if (req.getExpiresAt() != null) existing.setValidTo(req.getExpiresAt());

        // ✅ Only update active if client sent it
        if (req.getActive() != null) {
            existing.setActive(req.getActive());
        }

        
        if (req.getDiscountType() != null && !req.getDiscountType().isBlank()) {
            CouponDiscountType type = CouponDiscountType.valueOf(req.getDiscountType().toUpperCase());
            existing.setType(type);

            if (type == CouponDiscountType.FREE_SHIPPING) {
                existing.setValue(BigDecimal.ZERO);
            }

            // ✅ only percentage coupons may keep maxDiscountAmount
            if (type != CouponDiscountType.PERCENT) {
                existing.setMaxDiscountAmount(null);
            }
        }
        
        return couponRepository.save(existing);
    }

    @Override
    public void delete(Long ownerProjectId, Long id) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        Coupon existing = couponRepository.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found for this ownerProjectId"));

        couponRepository.delete(existing);
    }

    @Override
    public Coupon get(Long ownerProjectId, Long id) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        return couponRepository.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found for this ownerProjectId"));
    }

    @Override
    public List<Coupon> listByOwnerProject(Long ownerProjectId) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        return couponRepository.findByOwnerProjectId(ownerProjectId);
    }

    /* ============================
       VALIDATION / DISCOUNT LOGIC
       ============================ */

    @Override
    public Coupon validateForOrder(Long ownerProjectId,
                                   String code,
                                   BigDecimal itemsSubtotal) {

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        if (code == null || code.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_INVALID",
                    "Coupon code is required"
            );
        }

        Coupon coupon = couponRepository
                .findByOwnerProjectIdAndCodeIgnoreCase(ownerProjectId, code.trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "COUPON_INVALID",
                        "Coupon not found"
                ));

        if (!coupon.isActive()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_INVALID",
                    "Coupon is inactive"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_INVALID",
                    "Coupon is not active yet"
            );
        }

        if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_EXPIRED",
                    "Coupon is expired"
            );
        }

        if (coupon.getMinOrderAmount() != null
                && itemsSubtotal != null
                && itemsSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_MINIMUM_NOT_REACHED",
                    "Order minimum was not reached"
            );
        }

        if (coupon.getGlobalUsageLimit() != null
                && coupon.getUsedCount() != null
                && coupon.getUsedCount() >= coupon.getGlobalUsageLimit()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COUPON_USAGE_LIMIT_REACHED",
                    "Coupon usage limit reached"
            );
        }

        return coupon;
    }

    @Override
    public BigDecimal computeDiscount(Coupon coupon, BigDecimal itemsSubtotal) {
        if (coupon == null) return BigDecimal.ZERO;
        if (itemsSubtotal == null || itemsSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal base = itemsSubtotal;

        // FREE_SHIPPING handled elsewhere
        if (coupon.getType() == CouponDiscountType.FREE_SHIPPING) {
            return BigDecimal.ZERO;
        }

        BigDecimal value = coupon.getValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = BigDecimal.ZERO;

        if (coupon.getType() == CouponDiscountType.PERCENT) {
            discount = base.multiply(value)
                    .divide(BigDecimal.valueOf(100));

            // ✅ maxDiscountAmount applies ONLY to percentage coupons
            if (coupon.getMaxDiscountAmount() != null
                    && coupon.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                    && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                discount = coupon.getMaxDiscountAmount();
            }

        } else if (coupon.getType() == CouponDiscountType.FIXED) {
            // ✅ FIXED must always apply exact fixed value
            // ignore maxDiscountAmount completely
            discount = value;
        }

        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }

        // never discount more than subtotal
        if (discount.compareTo(base) > 0) {
            discount = base;
        }

        return discount;
    }
    
    /* ============================
       MAPPER
       ============================ */

    @Override
    public CouponResponse toResponse(Coupon c) {
        CouponResponse r = new CouponResponse();

        r.setId(c.getId());
        r.setOwnerProjectId(c.getOwnerProjectId());
        r.setCode(c.getCode());
        r.setDescription(c.getDescription());
        r.setDiscountType(c.getType() != null ? c.getType().name() : null);
        r.setDiscountValue(c.getValue());

        r.setMaxUses(c.getGlobalUsageLimit());
        r.setUsedCount(c.getUsedCount() == null ? 0 : c.getUsedCount());

        if (c.getGlobalUsageLimit() == null) {
            r.setRemainingUses(null); // unlimited
        } else {
            int used = c.getUsedCount() == null ? 0 : c.getUsedCount();
            r.setRemainingUses(Math.max(c.getGlobalUsageLimit() - used, 0));
        }

        r.setMinOrderAmount(c.getMinOrderAmount());
        r.setMaxDiscountAmount(c.getMaxDiscountAmount());
        r.setStartsAt(c.getValidFrom());
        r.setExpiresAt(c.getValidTo());
        r.setActive(c.isActive());

        LocalDateTime now = LocalDateTime.now();

        boolean started = c.getValidFrom() == null || !now.isBefore(c.getValidFrom());
        boolean expired = c.getValidTo() != null && now.isAfter(c.getValidTo());
        boolean usageLimitReached =
                c.getGlobalUsageLimit() != null
                        && (c.getUsedCount() == null ? 0 : c.getUsedCount()) >= c.getGlobalUsageLimit();

        boolean currentlyValid =
                c.isActive()
                        && started
                        && !expired
                        && !usageLimitReached;

        r.setStarted(started);
        r.setExpired(expired);
        r.setUsageLimitReached(usageLimitReached);
        r.setCurrentlyValid(currentlyValid);

        String status;
        if (!c.isActive()) {
            status = "INACTIVE";
        } else if (!started) {
            status = "SCHEDULED";
        } else if (expired) {
            status = "EXPIRED";
        } else if (usageLimitReached) {
            status = "USAGE_LIMIT_REACHED";
        } else {
            status = "ACTIVE";
        }

        r.setStatus(status);

        return r;
    }
}
