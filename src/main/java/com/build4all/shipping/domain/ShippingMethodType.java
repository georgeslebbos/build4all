package com.build4all.shipping.domain;

public enum ShippingMethodType {

    /**
     * A fixed flat price for shipping (e.g. 5$).
     * Uses ShippingMethod.flatRate.
     */
    FLAT_RATE,

    /**
     * Completely free shipping (always 0).
     */
    FREE,

    /**
     * Shipping cost based only on total weight.
     * Uses ShippingMethod.pricePerKg * totalWeight.
     */
    WEIGHT_BASED,

    /**
     * Shipping cost based on order price.
     * For now, we treat it like FLAT_RATE (flatRate),
     * but you can extend it later to percentage of subtotal.
     */
    PRICE_BASED,

    /**
     * Same semantics as WEIGHT_BASED – price per kg * total weight.
     * Alias for weight-based pricing.
     */
    PRICE_PER_KG,

    /**
     * Customer will pick up the order at the shop – no shipping cost.
     */
    LOCAL_PICKUP,

    /**
     * Shipping is free if items subtotal >= freeShippingThreshold,
     * otherwise falls back to flatRate (or per-kg if flatRate = 0).
     */
    FREE_OVER_THRESHOLD
}
