package com.build4all.licensing.guard;

import com.build4all.licensing.dto.OwnerAppAccessResponse;
import com.build4all.licensing.service.LicensingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OwnerSubscriptionGuard {

    private final LicensingService licensingService;

    public OwnerSubscriptionGuard(LicensingService licensingService) {
        this.licensingService = licensingService;
    }

    /**
     * Returns null if allowed.
     * Returns a ready ResponseEntity if blocked.
     */
    public ResponseEntity<?> blockIfWriteNotAllowed(Long ownerProjectId) {
        if (ownerProjectId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Owner project could not be resolved from token/request.",
                    "code", "OWNER_PROJECT_MISSING"
            ));
        }

        try {
            OwnerAppAccessResponse access = licensingService.getOwnerDashboardAccess(ownerProjectId);

            if (access == null || !access.isCanAccessDashboard()) {
                String reason = (access != null && access.getBlockingReason() != null)
                        ? access.getBlockingReason()
                        : "SUBSCRIPTION_LIMIT_EXCEEDED";

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("error", "Subscription limit exceeded. Upgrade your plan or reduce usage.");
                body.put("code", "SUBSCRIPTION_LIMIT_EXCEEDED");
                body.put("blockingReason", reason);
                body.put("ownerProjectId", ownerProjectId);

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
            }

            return null; // allowed
        } catch (Exception e) {
            // fail-closed (safer)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Unable to validate subscription right now.",
                    "code", "SUBSCRIPTION_CHECK_UNAVAILABLE"
            ));
        }
    }
}