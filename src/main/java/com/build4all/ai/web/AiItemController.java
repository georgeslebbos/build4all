package com.build4all.ai.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.ai.dto.AiChatResponse;
import com.build4all.ai.dto.AiItemChatRequest;
import com.build4all.ai.service.AiEntitlementService;
import com.build4all.ai.service.AiItemChatService;
import com.build4all.ai.service.AiUsageLimitService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiItemController {

    private final AiItemChatService service;
    private final AiEntitlementService entitlement;
    private final AiUsageLimitService usageLimit;
    private final AdminUserProjectRepository aupRepo;

    public AiItemController(
            AiItemChatService service,
            AiEntitlementService entitlement,
            AiUsageLimitService usageLimit,
            AdminUserProjectRepository aupRepo
    ) {
        this.service = service;
        this.entitlement = entitlement;
        this.usageLimit = usageLimit;
        this.aupRepo = aupRepo;
    }

    @PostMapping("/item-chat")
    public AiChatResponse itemChat(
            @RequestParam("ownerProjectLinkId") Long ownerProjectLinkId,
            @RequestBody AiItemChatRequest req
    ) {
        // 1) feature flag
        entitlement.assertAiEnabled(ownerProjectLinkId);

        // 2) owner usage limit (owner = AdminUser)
        Long ownerId = aupRepo.findOwnerIdByLinkId(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException("Owner not found for ownerProjectLinkId=" + ownerProjectLinkId));

        usageLimit.checkAndIncrement(ownerId);

        // 3) run AI
        return new AiChatResponse(service.handle(req));
    }
}
