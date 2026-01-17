
package com.build4all.ai.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.ai.dto.AiStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/ai")
public class PublicAiStatusController {

    private final AdminUserProjectRepository aupRepo;

    public PublicAiStatusController(AdminUserProjectRepository aupRepo) {
        this.aupRepo = aupRepo;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam("linkId") Long linkId) {

        Boolean enabled = aupRepo.isOwnerAiEnabledByLinkId(linkId).orElse(null);
        if (enabled == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Invalid linkId"));
        }

        Long ownerId = aupRepo.findOwnerIdByLinkId(linkId).orElse(null);
        String ownerName = aupRepo.findOwnerNameByLinkId(linkId).orElse(null);

        return ResponseEntity.ok(new AiStatusResponse(linkId, ownerId, ownerName, enabled));
    }
}
