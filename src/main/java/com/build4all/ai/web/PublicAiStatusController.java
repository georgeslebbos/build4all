package com.build4all.ai.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.ai.dto.AiStatusResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/ai")
public class PublicAiStatusController {

    private final AdminUserProjectRepository aupRepo;

    public PublicAiStatusController(AdminUserProjectRepository aupRepo) {
        this.aupRepo = aupRepo;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam("linkId") Long linkId) {

        // ✅ IMPORTANT:
        // - Do NOT reveal if linkId exists (prevents tenant enumeration)
        // - Do NOT leak ownerId/ownerName
        boolean enabled = aupRepo.isOwnerAiEnabledByLinkId(linkId).orElse(false);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AiStatusResponse(linkId, enabled));
    }
}