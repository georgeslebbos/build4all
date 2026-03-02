package com.build4all.app.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.dto.AppSupportInfoDto;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/apps")
public class AppSupportController {

    private final AdminUserProjectRepository linkRepo;
    private final JwtUtil jwtUtil;

    public AppSupportController(AdminUserProjectRepository linkRepo, JwtUtil jwtUtil) {
        this.linkRepo = linkRepo;
        this.jwtUtil = jwtUtil;
    }

    /**
     * ✅ In-app support info (SECURED)
     * - Reads tenant linkId from JWT (ownerProjectId claim)
     * - Works for USER / BUSINESS / OWNER / SUPER_ADMIN (if superadmin token includes ownerProjectId)
     *
     * GET /api/apps/support
     */
    @GetMapping(value = "/support", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSupportInfoFromToken(
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            // ✅ strict: must exist in token (for USER/BUSINESS/OWNER it always exists)
            Long linkId = jwtUtil.requireOwnerProjectId(authHeader);

            AppSupportInfoDto dto = linkRepo.findSupportInfoByLinkId(linkId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App not found: " + linkId));

            return ResponseEntity.ok(Map.of(
                    "linkId", dto.getLinkId(),
                    "ownerId", dto.getOwnerId(),
                    "ownerName", dto.getOwnerName() == null ? "" : dto.getOwnerName(),
                    "email", dto.getEmail() == null ? "" : dto.getEmail(),
                    "phoneNumber", dto.getPhoneNumber() == null ? "" : dto.getPhoneNumber()
            ));

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (RuntimeException ex) {
            // tenant missing / invalid token / etc
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal server error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

}