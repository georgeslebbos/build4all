package com.build4all.ai.web;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.ai.dto.OwnerAiToggleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/super")
public class SuperAdminAiController {

    private final AdminUsersRepository adminUsersRepo;

    public SuperAdminAiController(AdminUsersRepository adminUsersRepo) {
        this.adminUsersRepo = adminUsersRepo;
    }

    // ✅ Get AI status for an owner
    @GetMapping("/owners/{ownerId}/ai")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> getOwnerAi(@PathVariable Long ownerId) {

        AdminUser owner = adminUsersRepo.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));

        return ResponseEntity.ok(Map.of(
                "ownerId", ownerId,
                "aiEnabled", owner.isAiEnabled()
        ));
    }

    // ✅ Toggle AI for an owner
    @PatchMapping("/owners/{ownerId}/ai")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> toggleOwnerAi(
            @PathVariable Long ownerId,
            @RequestBody OwnerAiToggleRequest body
    ) {
        AdminUser owner = adminUsersRepo.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));

        owner.setAiEnabled(body.isEnabled());
        adminUsersRepo.save(owner);

        return ResponseEntity.ok(Map.of(
                "message", "AI updated",
                "ownerId", ownerId,
                "aiEnabled", owner.isAiEnabled()
        ));
    }
}
