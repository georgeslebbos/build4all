package com.build4all.payment.web;

import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.domain.PaymentMethodConfig;
import com.build4all.payment.dto.SavePaymentMethodConfigRequest;
import com.build4all.payment.gateway.PaymentGatewayRegistry;
import com.build4all.payment.repository.PaymentMethodConfigRepository;
import com.build4all.payment.repository.PaymentMethodRepository;
import com.build4all.payment.service.PaymentConfigService;
import com.build4all.security.JwtUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/owner/projects/{ownerProjectId}/payment")
// This controller is the OWNER dashboard API for payment plugins.
// It allows a project owner to:
// - list all platform-enabled gateways (Stripe/Cash/PayPal...)
// - see per-project enabled/disabled status
// - view gateway config schema (dynamic form fields)
// - save/update gateway configJson values per project
//
// WooCommerce analogy:
// /methods endpoint = list installed payment plugins + settings form
// /methods/{methodName} = save plugin settings for this store
public class OwnerPaymentConfigController {

    private final PaymentMethodRepository methodRepo;
    private final PaymentMethodConfigRepository configRepo;
    private final PaymentGatewayRegistry registry;
    private final PaymentConfigService configService;
    private final JwtUtil jwtUtil;

    public OwnerPaymentConfigController(PaymentMethodRepository methodRepo,
                                        PaymentMethodConfigRepository configRepo,
                                        PaymentGatewayRegistry registry,
                                        PaymentConfigService configService,
                                        JwtUtil jwtUtil) {
        this.methodRepo = methodRepo;
        this.configRepo = configRepo;
        this.registry = registry;
        this.configService = configService;
        this.jwtUtil = jwtUtil;
    }

    // Extract JWT token from "Bearer xxx"
    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    // Simple role check (you can later replace with Spring Security annotations)
    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        for (String r : roles) if (r.equalsIgnoreCase(role)) return true;
        return false;
    }

    /**
     * GET /methods
     *
     * Returns:
     * - platform enabled payment methods (PaymentMethod.enabled = true)
     * - merged with this project config (PaymentMethodConfig)
     * - plus the dynamic schema from the plugin (PaymentGateway.configSchema)
     * - plus current saved config values (configJson -> Map)
     *
     * This is used by the owner UI to render a settings page for all payment gateways.
     */
    @GetMapping("/methods")
    @Transactional(readOnly = true)
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth,
                                  @PathVariable Long ownerProjectId) {

        // Only OWNER / SUPER_ADMIN can manage payment settings
        if (!hasRole(strip(auth), "OWNER", "SUPER_ADMIN")) {
            return ResponseEntity.status(403)
                    .body(Map.of("message","OWNER or SUPER_ADMIN required"));
        }

        // Platform-level enabled gateways ("installed plugins")
        List<PaymentMethod> platformEnabled = methodRepo.findByEnabledTrue();

        // Output list for UI
        List<Map<String, Object>> out = new ArrayList<>();

        for (PaymentMethod m : platformEnabled) {

            // Ensure there is a gateway plugin for this PaymentMethod.
            // If PaymentMethod exists in DB but plugin is missing, this throws.
            // That helps catch misconfiguration early.
            var gw = registry.require(m.getName());

            // Fetch per-project config row (may not exist yet)
            PaymentMethodConfig cfg = configRepo
                    .findByOwnerProjectIdAndPaymentMethod_NameIgnoreCase(ownerProjectId, m.getName())
                    .orElse(null);

            // Build a UI row
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", m.getName()); // gateway code, e.g. STRIPE
            row.put("platformEnabled", m.isEnabled()); // should be true because we fetched enabled only
            row.put("projectEnabled", cfg != null && cfg.isEnabled()); // store toggle

            // Dynamic form schema defined by the plugin (Stripe/Cash/PayPal...)
            row.put("configSchema", gw.configSchema());

            // Current values saved by owner (parsed from configJson)
            // WARNING: this may include secrets if your configJson contains secretKey/webhookSecret.
            // In a real-world system, you should mask secrets or avoid returning them.
            row.put("configValues",
                    cfg == null ? Map.of()
                            : configService.parse(cfg.getConfigJson()).values()
            );

            out.add(row);
        }

        return ResponseEntity.ok(out);
    }

    /**
     * PUT /methods/{methodName}
     *
     * Saves or updates config for ONE gateway for this project:
     * - enabled (true/false)
     * - configValues (map) -> stored as configJson
     *
     * Example:
     * PUT /methods/STRIPE
     * body: { enabled: true, configValues: { secretKey: "...", publishableKey: "..."} }
     */
    @PutMapping("/methods/{methodName}")
    @Transactional
    public ResponseEntity<?> save(@RequestHeader("Authorization") String auth,
                                  @PathVariable Long ownerProjectId,
                                  @PathVariable String methodName,
                                  @RequestBody SavePaymentMethodConfigRequest body) {

        if (!hasRole(strip(auth), "OWNER", "SUPER_ADMIN")) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "OWNER or SUPER_ADMIN required"));
        }

        final String code = methodName == null ? "" : methodName.trim().toUpperCase();

        try {
            // 1) Ensure gateway exists
            registry.require(code);

            // 2) Ensure PaymentMethod exists
            PaymentMethod method = methodRepo.findByNameIgnoreCase(code)
                    .orElseThrow(() -> new IllegalArgumentException("PaymentMethod not found: " + code));

            // 3) Load existing config if any
            PaymentMethodConfig cfg = configRepo
                    .findByOwnerProjectIdAndPaymentMethod_NameIgnoreCase(ownerProjectId, code)
                    .orElse(null);

            // ✅ DISABLE: do NOT touch configJson
            if (!body.isEnabled()) {
                if (cfg != null) {
                    cfg.setEnabled(false);
                    configRepo.save(cfg);
                }
                return ResponseEntity.ok(Map.of(
                        "ownerProjectId", ownerProjectId,
                        "methodName", method.getName(),
                        "enabled", false
                ));
            }

            // ✅ ENABLE: create if missing
            if (cfg == null) cfg = new PaymentMethodConfig();

            cfg.setOwnerProjectId(ownerProjectId);
            cfg.setPaymentMethod(method);
            cfg.setEnabled(true);

            // handle null safely
            Map<String, Object> incoming = body.getConfigValues();
            if (incoming == null) incoming = new HashMap<>();

            // Optional (recommended): merge with existing so empty password fields don't wipe secrets
            Map<String, Object> merged = new HashMap<>();
            if (cfg.getConfigJson() != null && !cfg.getConfigJson().isBlank()) {
                try {
                    merged.putAll(configService.parse(cfg.getConfigJson()).values());
                } catch (Exception ignore) {
                    // if old JSON is corrupted, we just overwrite with incoming
                }
            }
            merged.putAll(incoming);

            cfg.setConfigJson(configService.toJson(merged));

            PaymentMethodConfig saved = configRepo.save(cfg);

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "ownerProjectId", saved.getOwnerProjectId(),
                    "methodName", saved.getPaymentMethod().getName(),
                    "enabled", saved.isEnabled(),
                    "updatedAt", saved.getUpdatedAt()
            ));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            // don't leak stack trace to client
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save payment config"));
        }
    }
}