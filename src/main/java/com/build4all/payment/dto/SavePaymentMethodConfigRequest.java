package com.build4all.payment.dto;

import java.util.Map;

/**
 * This DTO is sent from the OWNER dashboard to the backend
 * to save/update payment gateway settings for ONE project.
 *
 * It is used by:
 * PUT /api/owner/projects/{ownerProjectId}/payment/methods/{methodName}
 *
 * WooCommerce analogy:
 * "Save plugin settings" (enable/disable + settings fields)
 */
public class SavePaymentMethodConfigRequest {

    /**
     * Whether this gateway is enabled for this specific project (store).
     * - true  => gateway appears in checkout options for this project
     * - false => gateway hidden/disabled for this project
     */
    private boolean enabled;

    /**
     * Dynamic configuration values for the gateway.
     * This is stored as JSON string in PaymentMethodConfig.configJson.
     *
     * Example for Stripe:
     * {
     *   "secretKey": "sk_test_...",
     *   "publishableKey": "pk_test_...",
     *   "webhookSecret": "whsec_...",
     *   "platformFeePct": 10
     * }
     *
     * Example for Cash:
     * { "instructions": "Pay on delivery" }
     *
     * Example for PayPal:
     * { "clientId": "...", "clientSecret": "...", "mode": "SANDBOX" }
     */
    private Map<String, Object> configValues;

    // ---- Getters/Setters ----

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, Object> getConfigValues() { return configValues; }
    public void setConfigValues(Map<String, Object> configValues) { this.configValues = configValues; }
}
