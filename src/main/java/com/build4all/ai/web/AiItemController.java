package com.build4all.ai.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.ai.dto.AiChatResponse;
import com.build4all.ai.dto.AiItemChatRequest;
import com.build4all.ai.service.AiEntitlementService;
import com.build4all.ai.service.AiItemChatService;
import com.build4all.ai.service.AiUsageLimitService;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyRole('OWNER''USER','SUPER_ADMIN')")
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
            @RequestParam("ownerProjectLinkId") Long ownerProjectLinkId,
            @RequestBody AiItemChatRequest req
    ) {
        String token = extractToken(authHeader);

        // ✅ role gate (extra safety حتى مع @PreAuthorize)
        String role = jwtUtil.extractRole(token);
        boolean isSuper = "SUPER_ADMIN".equalsIgnoreCase(role);

        // ✅ ownerId from token
        Long tokenOwnerId = jwtUtil.extractAdminId(token);
        if (tokenOwnerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing admin id");
        }

        // ✅ ownership check (ONLY if not super admin)
        Long linkOwnerId = aupRepo.findOwnerIdByLinkId(ownerProjectLinkId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Owner not found for ownerProjectLinkId=" + ownerProjectLinkId
                ));

        if (!isSuper && !tokenOwnerId.equals(linkOwnerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: link does not belong to this owner");
        }

        // 1) feature flag
        entitlement.assertAiEnabled(ownerProjectLinkId);

        // 2) usage limit for owner
        usageLimit.checkAndIncrement(linkOwnerId);

        // 3) run AI
        return new AiChatResponse(service.handle(req));
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid Authorization header");
        }
        return authHeader.replace("Bearer ", "").trim();
    }
}