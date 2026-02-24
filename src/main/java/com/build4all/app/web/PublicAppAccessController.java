package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicAppAccessController {

    private final AdminUserProjectRepository aupRepo;

    public PublicAppAccessController(AdminUserProjectRepository aupRepo) {
        this.aupRepo = aupRepo;
    }

    @GetMapping(value = "/app-access/{linkId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> checkAppAccessByLinkId(@PathVariable Long linkId) {
        AdminUserProject link = aupRepo.findById(linkId).orElse(null);

        // ✅ treat missing same as gone (clean public API)
        if (link == null) {
            return ResponseEntity.status(HttpStatus.GONE).body(goneBody(
                    "APP_NOT_AVAILABLE",
                    "This app is no longer available",
                    linkId
            ));
        }

        // ✅ soft deleted
        if (link.isDeleted()) {
            return ResponseEntity.status(HttpStatus.GONE).body(goneBody(
                    "APP_DELETED",
                    "This app is no longer available",
                    linkId
            ));
        }

        // ✅ optional: expired
        if (link.getEndTo() != null && link.getEndTo().isBefore(LocalDate.now())) {
            return ResponseEntity.status(HttpStatus.GONE).body(goneBody(
                    "APP_EXPIRED",
                    "This app is no longer available",
                    linkId
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("allowed", true);
        body.put("reason", "");
        body.put("message", "OK");

        body.put("ownerProjectLinkId", link.getId());
        body.put("ownerId", link.getAdmin() != null ? link.getAdmin().getAdminId() : null);
        body.put("projectId", link.getProject() != null ? link.getProject().getId() : null);
        body.put("slug", link.getSlug());
        body.put("appName", link.getAppName());
        body.put("status", link.getStatus());
        body.put("validFrom", link.getValidFrom());
        body.put("endTo", link.getEndTo());

        return ResponseEntity.ok(body);
    }

    private Map<String, Object> goneBody(String reason, String message, Long linkId) {
        Map<String, Object> body = new HashMap<>();
        body.put("allowed", false);
        body.put("reason", reason);
        body.put("message", message);
        body.put("ownerProjectLinkId", linkId);
        return body;
    }
}