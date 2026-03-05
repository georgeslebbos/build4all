package com.build4all.ai.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.ai.dto.AiChatResponse;
import com.build4all.ai.dto.AiItemChatRequest;
import com.build4all.ai.service.AiEntitlementService;
import com.build4all.ai.service.AiItemChatService;
import com.build4all.ai.service.AiUsageLimitService;
import com.build4all.security.JwtUtil;
import com.build4all.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyRole('OWNER','USER','SUPER_ADMIN')") // ✅ FIXED
public class AiItemController {

    private final AiItemChatService service;
    private final AiEntitlementService entitlement;
    private final AiUsageLimitService usageLimit;
    private final AdminUserProjectRepository aupRepo;
    private final JwtUtil jwtUtil;

    public AiItemController(
            AiItemChatService service,
            AiEntitlementService entitlement,
            AiUsageLimitService usageLimit,
            AdminUserProjectRepository aupRepo,
            JwtUtil jwtUtil
    ) {
        this.service = service;
        this.entitlement = entitlement;
        this.usageLimit = usageLimit;
        this.aupRepo = aupRepo;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/item-chat")
    public AiChatResponse itemChat(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiItemChatRequest req
    ) {
        String token = extractToken(authHeader);

        // ✅ role
        String role = jwtUtil.extractRole(token);
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token role");
        }
        boolean isSuper = "SUPER_ADMIN".equalsIgnoreCase(role);

        // ✅ tenant (AUP id) comes from JWT claim "ownerProjectId"
        final Long aupId;
        try {
            aupId = jwtUtil.requireOwnerProjectId(token); // works for USER/OWNER/SUPER_ADMIN
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }

        // ✅ Owner id for this tenant (needed for usage limit + ownership checks)
        Long linkOwnerId = aupRepo.findOwnerIdByLinkId(aupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Owner not found for ownerProjectId=" + aupId
                ));

        // ✅ Ownership check ONLY for OWNER role (USER has no adminId)
        if (!isSuper && "OWNER".equalsIgnoreCase(role)) {
            Long tokenOwnerId = jwtUtil.extractAdminId(token);
            if (tokenOwnerId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing admin id");
            }
            if (!tokenOwnerId.equals(linkOwnerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: link does not belong to this owner");
            }
        }

        // ✅ Set tenant for the service layer (ThreadLocal)
        TenantContext.setOwnerProjectId(aupId);

        try {
            // 1) feature flag
            entitlement.assertAiEnabled(aupId);

            // 2) usage limit for OWNER (owner-based)
            usageLimit.checkAndIncrement(linkOwnerId);

            // 3) run AI (service will load item by tenant from TenantContext)
            return new AiChatResponse(service.handle(req));

        } finally {
            // ✅ CRITICAL: prevent ThreadLocal leaking between requests
            TenantContext.clear();
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid Authorization header");
        }
        return authHeader.substring(7).trim();
    }
}