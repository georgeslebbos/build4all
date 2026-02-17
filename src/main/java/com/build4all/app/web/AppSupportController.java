package com.build4all.app.web;

import com.build4all.app.dto.AppSupportInfoDto;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/apps")
public class AppSupportController {

    private final AdminUserProjectRepository linkRepo;

    public AppSupportController(AdminUserProjectRepository linkRepo) {
        this.linkRepo = linkRepo;
    }

    /**
     * ✅ Public support info for an app by linkId
     * GET /api/apps/{linkId}/support
     */
    @GetMapping("/{linkId}/support")
    public ResponseEntity<?> getSupportInfo(@PathVariable Long linkId) {
        try {
            AppSupportInfoDto dto = linkRepo.findSupportInfoByLinkId(linkId)
                    .orElseThrow(() -> new IllegalArgumentException("App not found: " + linkId));

            // ✅ frontend-friendly (no nulls)
            return ResponseEntity.ok(Map.of(
                    "linkId", dto.getLinkId(),
                    "ownerId", dto.getOwnerId(),
                    "ownerName", dto.getOwnerName() == null ? "" : dto.getOwnerName(),
                    "email", dto.getEmail() == null ? "" : dto.getEmail(),
                    "phoneNumber", dto.getPhoneNumber() == null ? "" : dto.getPhoneNumber()
            ));

        } catch (IllegalArgumentException nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", nf.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal server error", "error", e.getMessage()));
        }
    }
}
