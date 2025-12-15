package com.build4all.payment.gateway.dto;

import java.util.Map;

/**
 * GatewayConfig is a simple wrapper around a Map<String,Object>
 * that contains the per-project gateway settings loaded from DB (configJson).
 *
 * Example configJson for Stripe:
 * {
 *   "secretKey": "sk_test_...",
 *   "publishableKey": "pk_test_...",
 *   "webhookSecret": "whsec_...",
 *   "platformFeePct": 10
 * }
 *
 * PaymentConfigService.parse(configJson) -> Map -> new GatewayConfig(map)
 * Then the gateway reads values using helper methods.
 */
public class GatewayConfig {

    /**
     * Raw config values (dynamic keys, different per gateway).
     * Stripe keys, PayPal keys, cash instructions, etc.
     */
    private final Map<String, Object> values;

    public GatewayConfig(Map<String, Object> values) {
        // If PaymentConfigService returns emptyMap, values is still non-null.
        this.values = values;
    }

    /**
     * Exposes the raw map (used by owner UI to show saved values).
     */
    public Map<String, Object> values() {
        return values;
    }

    /**
     * Get a config value as string.
     *
     * Example:
     * cfg.getString("secretKey") -> "sk_test_..."
     *
     * If key doesn't exist => returns null
     */
    public String getString(String key) {
        Object v = values.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * Get a config value as Double with a default fallback.
     *
     * Example:
     * cfg.getDouble("platformFeePct", 10.0) -> 10.0
     *
     * Handles cases where value is stored as:
     * - number (10)
     * - string ("10")
     * - invalid ("abc") -> returns default
     */
    public Double getDouble(String key, Double def) {
        Object v = values.get(key);
        if (v == null) return def;
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Checks if a key exists (even if value is null).
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }
}
