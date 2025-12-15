package com.build4all.payment.service;

import com.build4all.payment.domain.PaymentMethodConfig;
import com.build4all.payment.gateway.dto.GatewayConfig;
import com.build4all.payment.repository.PaymentMethodConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
// This service is responsible for:
// 1) Loading project-specific payment gateway config from DB (PaymentMethodConfig)
// 2) Ensuring the gateway is enabled for that project
// 3) Parsing configJson (String) -> GatewayConfig (Map wrapper)
// 4) Serializing Map -> JSON for saving config
public class PaymentConfigService {

    private final PaymentMethodConfigRepository configRepo;

    // ObjectMapper is used to parse/serialize the config JSON.
    // Note: creating a new ObjectMapper manually is okay,
    // but best practice is injecting Spring's shared ObjectMapper.
    private final ObjectMapper mapper = new ObjectMapper();

    public PaymentConfigService(PaymentMethodConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    /**
     * Loads a project configuration for a given gateway name and enforces:
     * - config row exists
     * - config is enabled for that project
     *
     * Used during checkout/startPayment.
     * Example:
     *   requireEnabled(100, "STRIPE") -> returns PaymentMethodConfig with Stripe keys
     */
    public PaymentMethodConfig requireEnabled(Long ownerProjectId, String methodName) {

        // Look for config row by (ownerProjectId + payment_methods.name)
        PaymentMethodConfig cfg = configRepo
                .findByOwnerProjectIdAndPaymentMethod_NameIgnoreCase(ownerProjectId, methodName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment method not configured for project: " + methodName
                ));

        // Even if the method exists on platform, it may be disabled for this project.
        if (!cfg.isEnabled())
            throw new IllegalStateException("Payment method disabled for project: " + methodName);

        return cfg;
    }

    /**
     * Parses configJson (String) into GatewayConfig.
     *
     * GatewayConfig is basically a wrapper around Map<String,Object>
     * with convenience getters like getString("secretKey").
     *
     * If configJson is empty => return empty map config.
     */
    public GatewayConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return new GatewayConfig(Collections.emptyMap());
        }

        try {
            // Convert JSON string into a Map (dynamic keys: secretKey, mode, etc.)
            Map<String, Object> values = mapper.readValue(json, new TypeReference<>() {});
            return new GatewayConfig(values);
        } catch (Exception e) {
            // If owner stored invalid JSON, fail fast so gateway doesn't run with bad config.
            throw new IllegalStateException("Invalid configJson: " + e.getMessage());
        }
    }

    /**
     * Serializes config values into JSON string for storage in PaymentMethodConfig.configJson.
     *
     * Used when owner saves payment settings from dashboard.
     */
    public String toJson(Map<String, Object> values) {
        try {
            // Ensure we don't write "null" (store {} instead)
            return mapper.writeValueAsString(values == null ? Collections.emptyMap() : values);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize configJson: " + e.getMessage());
        }
    }
}
