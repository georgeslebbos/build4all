package com.build4all.project.web;

import com.build4all.project.domain.Project;
import com.build4all.project.service.ProjectService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;
    private final JwtUtil jwt;

    public ProjectController(ProjectService service, JwtUtil jwt) {
        this.service = service;
        this.jwt = jwt;
    }

    // --- helpers -------------------------------------------------------------

    private String tokenFromAuth(String auth) {
        if (auth == null) return null;
        return auth.startsWith("Bearer ") ? auth.substring(7).trim() : auth.trim();
    }

    private boolean allowCreateOrUpdate(String auth) {
        String token = tokenFromAuth(auth);
        return token != null && (jwt.isAdminToken(token) || jwt.isBusinessToken(token) || jwt.isOwnerToken(token));
    }

    private boolean allowDelete(String auth) {
        String token = tokenFromAuth(auth);
        return token != null && jwt.isAdminToken(token); // admin-only delete
    }

    private boolean isOwner(String auth) {
        String token = tokenFromAuth(auth);
        return token != null && jwt.isOwnerToken(token);
    }

    private Long callerAdminId(String auth) {
        String token = tokenFromAuth(auth);
        if (token == null) return null;
        try {
            // your JwtUtil exposes extractId(); admin tokens store "id" = admin_id
            return jwt.extractId(token);
        } catch (Exception e) {
            return null;
        }
    }

    // --- endpoints -----------------------------------------------------------

    @Operation(summary = "Create a project")
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("Authorization") String auth,
                                    @RequestBody Map<String, Object> body) {
        if (!allowCreateOrUpdate(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        try {
            String name = (String) body.get("projectName");
            String description = (String) body.get("description");
            Boolean active = body.get("active") == null ? null : Boolean.valueOf(body.get("active").toString());

            Project p = service.create(name, description, active);

            // If Owner: link to self and force pending (inactive)
            if (isOwner(auth)) {
                Long adminId = callerAdminId(auth);
                if (adminId != null) {
                    service.linkProjectToOwner(adminId, p.getId());
                }
                if (Boolean.TRUE.equals(p.getActive())) {
                    p.setActive(false); // owners can't self-activate
                    p = service.save(p);
                }
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(p);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "List all projects")
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @Operation(summary = "List projects linked to the caller (Owner/Admin)")
    @GetMapping("/mine")
    public ResponseEntity<?> mine(@RequestHeader("Authorization") String auth) {
        String token = tokenFromAuth(auth);
        if (token == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        if (jwt.isOwnerToken(token) || jwt.isAdminToken(token)) {
            Long adminId = jwt.extractId(token);
            return ResponseEntity.ok(service.findByOwnerAdminId(adminId));
        }
        return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
    }

    @Operation(summary = "Get project by id")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Project p = service.findById(id);
        return p == null ? ResponseEntity.status(404).build() : ResponseEntity.ok(p);
    }

    @Operation(summary = "Update a project")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        if (!allowCreateOrUpdate(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        try {
            String name = (String) body.get("projectName");
            String description = (String) body.get("description");
            Boolean active = body.get("active") == null ? null : Boolean.valueOf(body.get("active").toString());

            // Optional safety: owners may edit only their projects and can't activate
            if (isOwner(auth)) {
                Long adminId = callerAdminId(auth);
                if (adminId == null || !service.isOwnerLinkedToProject(adminId, id)) {
                    return ResponseEntity.status(403).body(Map.of("error", "Not your project"));
                }
                if (Boolean.TRUE.equals(active)) {
                    active = false; // prevent self-activation
                }
            }

            return ResponseEntity.ok(service.update(id, name, description, active));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Delete a project (admin only)")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        if (!allowDelete(auth)) return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
