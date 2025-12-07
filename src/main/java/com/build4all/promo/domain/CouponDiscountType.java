package com.build4all.promo.domain;

public enum CouponDiscountType {
    PERCENT,       // value = % (e.g. 10 -> 10%)
    FIXED,         // value = fixed amount in currency
    FREE_SHIPPING  // special type: free shipping
}
