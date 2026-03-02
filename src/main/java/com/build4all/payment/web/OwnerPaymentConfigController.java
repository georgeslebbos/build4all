package com.build4all.payment.web;

import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.domain.PaymentMethodConfig;
import com.build4all.payment.dto.SavePaymentMethodConfigRequest;
import com.build4all.payment.gateway.PaymentGatewayRegistry;
import com.build4all.payment.repository.PaymentMethodConfigRepository;
import com.build4all.payment.repository.PaymentMethodRepository;
import com.build4all.payment.service.PaymentConfigService;
import com.build4all.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/owner/projects/payment")
public class OwnerPaymentConfigController {

    private final PaymentMethodRepository methodRepo;
    private final PaymentMethodConfigRepository configRepo;
    private final PaymentGatewayRegistry registry;
    private final PaymentConfigService configService;
    private final JwtUtil jwtUtil;

    public OwnerPaymentConfigController(
            PaymentMethodRepository methodRepo,
            PaymentMethodConfigRepository configRepo,
            PaymentGatewayRegistry registry,
            PaymentConfigService configService,
            JwtUtil jwtUtil
    ) {
        this.methodRepo = methodRepo;
        this.configRepo = configRepo;
        this.registry = registry;
        this.configService = configService;
        this.jwtUtil = jwtUtil;
    }

    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private Long ownerProjectIdFromToken(String auth) {
        String token = strip(auth);
        // ✅ adjust to your claim name
        // examples: extractOwnerProjectId / extractOwnerProjectLinkId
        Long ownerProjectId = jwtUtil.extractOwnerProjectId(token);
        if (ownerProjectId == null) throw new IllegalArgumentException("OWNER_PROJECT_ID_MISSING");
        return ownerProjectId;
    }

    /** OWNER: must match tenant; SUPER_ADMIN: allow */
    private void enforceOwnerScopeIfNeeded(String auth, Long ownerProjectId) {
        String token = strip(auth);
        String role = jwtUtil.extractRole(token);
        if (role == null) throw new IllegalArgumentException("INVALID_TOKEN");

        if ("OWNER".equalsIgnoreCase(role)) {
            try {
                jwtUtil.requireTenantMatch(token, ownerProjectId);
            } catch (RuntimeException ex) {
                throw new NoSuchElementException("OWNER_PROJECT_NOT_FOUND");
            }
        }
    }

    /** mask secrets to avoid leaking them back to UI */
    private Map<String, Object> maskSecrets(Map<String, Object> values) {
        if (values == null) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>(values);

        List<String> secretKeys = List.of(
                "secretKey", "webhookSecret", "apiKey", "privateKey",
                "clientSecret", "token", "accessToken"
        );

        for (String k : secretKeys) {
            if (out.containsKey(k) && out.get(k) != null && !String.valueOf(out.get(k)).isBlank()) {
                out.put(k, "********");
            }
        }
        return out;
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @GetMapping("/methods")
    @Transactional(readOnly = true)
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth) {

        Long ownerProjectId = ownerProjectIdFromToken(auth);
        enforceOwnerScopeIfNeeded(auth, ownerProjectId);

        List<PaymentMethod> platformEnabled = methodRepo.findByEnabledTrue();
        List<Map<String, Object>> out = new ArrayList<>();

        for (PaymentMethod m : platformEnabled) {
            var gw = registry.require(m.getName());

            PaymentMethodConfig cfg = configRepo
                    .findByOwnerProjectIdAndPaymentMethod_NameIgnoreCase(ownerProjectId, m.getName())
                    .orElse(null);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", m.getName());
            row.put("platformEnabled", m.isEnabled());
            row.put("projectEnabled", cfg != null && cfg.isEnabled());
            row.put("configSchema", gw.configSchema());

            Map<String, Object> currentValues =
                    (cfg == null) ? Map.of() : configService.parse(cfg.getConfigJson()).values();

            row.put("configValues", maskSecrets(currentValues));
            out.add(row);
        }

        return ResponseEntity.ok(out);
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @PutMapping("/methods/{methodName}")
    @Transactional
    public ResponseEntity<?> save(
            @RequestHeader("Authorization") String auth,
            @PathVariable String methodName,
            @RequestBody SavePaymentMethodConfigRequest body
    ) {
        Long ownerProjectId = ownerProjectIdFromToken(auth);
        enforceOwnerScopeIfNeeded(auth, ownerProjectId);

        final String code = methodName == null ? "" : methodName.trim().toUpperCase(Locale.ROOT);

        try {
            registry.require(code);

            PaymentMethod method = methodRepo.findByNameIgnoreCase(code)
                    .orElseThrow(() -> new IllegalArgumentException("PaymentMethod not found: " + code));

            PaymentMethodConfig cfg = configRepo
                    .findByOwnerProjectIdAndPaymentMethod_NameIgnoreCase(ownerProjectId, code)
                    .orElse(null);

            boolean enable = (body != null && body.isEnabled());

            if (!enable) {
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

            if (cfg == null) cfg = new PaymentMethodConfig();
            cfg.setOwnerProjectId(ownerProjectId);
            cfg.setPaymentMethod(method);
            cfg.setEnabled(true);

            Map<String, Object> incoming = (body == null || body.getConfigValues() == null)
                    ? new HashMap<>()
                    : new HashMap<>(body.getConfigValues());

            Map<String, Object> merged = new HashMap<>();
            if (cfg.getConfigJson() != null && !cfg.getConfigJson().isBlank()) {
                try {
                    merged.putAll(configService.parse(cfg.getConfigJson()).values());
                } catch (Exception ignore) { }
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
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save payment config"));
        }
    }
}