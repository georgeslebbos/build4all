package com.build4all.admin.web;

import com.build4all.admin.dto.AdminProjectAssignmentRequest;
import com.build4all.admin.dto.AdminProjectAssignmentResponse;
import com.build4all.admin.service.AdminUserProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin-users/{adminId}/projects")
public class AdminUserProjectsController {

    private final AdminUserProjectService service;

    public AdminUserProjectsController(AdminUserProjectService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AdminProjectAssignmentResponse>> list(@PathVariable Long adminId) {
        return ResponseEntity.ok(service.list(adminId));
    }

    /** Create or overwrite assignment with license fields */
    @PostMapping
    public ResponseEntity<Void> assign(@PathVariable Long adminId,
                                       @RequestBody AdminProjectAssignmentRequest req) {
        service.assign(adminId, req);
        return ResponseEntity.noContent().build();
    }

    /** Update only license/validity for an existing assignment */
    @PutMapping("/{projectId}")
    public ResponseEntity<Void> update(@PathVariable Long adminId,
                                       @PathVariable Long projectId,
                                       @RequestBody AdminProjectAssignmentRequest req) {
        service.updateLicense(adminId, projectId, req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> remove(@PathVariable Long adminId, @PathVariable Long projectId) {
        service.remove(adminId, projectId);
        return ResponseEntity.noContent().build();
    }
}
