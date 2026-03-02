package com.build4all.project.web;

import com.build4all.project.domain.Project;
import com.build4all.project.domain.ProjectType;
import com.build4all.project.service.ProjectService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
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

    /* ========================= helpers ========================= */

    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private String roleOf(String auth) {
        return jwt.extractRole(strip(auth));
    }

    private Long callerId(String auth) {
        return jwt.extractId(strip(auth));
    }

    private static String asString(Map<String, Object> body, String key) {
        Object v = (body == null) ? null : body.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static Boolean asBool(Map<String, Object> body, String key) {
        Object v = (body == null) ? null : body.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        if (s.isBlank()) return null;
        return Boolean.parseBoolean(s);
    }

    private ProjectType parseProjectType(Object raw) {
        if (raw == null) return null;
        String v = raw.toString().trim().toUpperCase(Locale.ROOT);
        if (v.isBlank()) return null;
        try {
            return ProjectType.valueOf(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid projectType. Allowed: ECOMMERCE, SERVICES, ACTIVITIES");
        }
    }

    private boolean isOwner(String auth) {
        String r = roleOf(auth);
        return r != null && r.equalsIgnoreCase("OWNER");
    }

    /* ========================= endpoints ========================= */

    @Operation(summary = "Create a project")
    @PreAuthorize("hasRole('SUPER_ADMIN') ")
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String auth,
            @RequestBody Map<String, Object> body
    ) {
        try {
            String name = asString(body, "projectName");
            String description = asString(body, "description");
            Boolean active = asBool(body, "active");
            ProjectType projectType = parseProjectType(body == null ? null : body.get("projectType"));

            Project p = service.create(name, description, active, projectType);

            // ✅ Owner behavior: link to self, cannot self-activate
            if (isOwner(auth)) {
                Long ownerAdminId = callerId(auth);
                service.linkProjectToOwner(ownerAdminId, p.getId());

                if (Boolean.TRUE.equals(p.getActive())) {
                    p.setActive(false);
                    p = service.save(p);
                }
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(p);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @Operation(summary = "List all projects")
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @Operation(summary = "List projects linked to the caller (Owner/Admin)")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('OWNER')")
    @GetMapping("/mine")
    public ResponseEntity<?> mine(@RequestHeader("Authorization") String auth) {
        try {
            Long adminId = callerId(auth);
            return ResponseEntity.ok(service.findByOwnerAdminId(adminId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @Operation(summary = "Get project by id")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Project p = service.findById(id);
        return (p == null)
                ? ResponseEntity.status(404).body(Map.of("error", "Project not found"))
                : ResponseEntity.ok(p);
    }

    @Operation(summary = "Update a project")
    @PreAuthorize("hasRole('SUPER_ADMIN') ")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        try {
            String name = asString(body, "projectName");
            String description = asString(body, "description");
            Boolean active = asBool(body, "active");
            ProjectType projectType = parseProjectType(body == null ? null : body.get("projectType"));

            // ✅ Owner safety: only edit own linked projects + cannot activate
            if (isOwner(auth)) {
                Long ownerAdminId = callerId(auth);
                if (!service.isOwnerLinkedToProject(ownerAdminId, id)) {
                    return ResponseEntity.status(403).body(Map.of("error", "Not your project"));
                }
                if (Boolean.TRUE.equals(active)) active = false;
            }

            return ResponseEntity.ok(service.update(id, name, description, active, projectType));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @Operation(summary = "Delete a project (admin only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @Operation(summary = "List owners for a project (with appsCount)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/{id}/owners")
    public ResponseEntity<?> ownersByProject(
            @RequestHeader("Authorization") String auth,
            @PathVariable("id") Long projectId
    ) {
        try {
            return ResponseEntity.ok(service.ownersByProject(projectId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }

    @Operation(summary = "List apps for an owner inside a project")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/{projectId}/owners/{adminId}/apps")
    public ResponseEntity<?> appsByProjectAndOwner(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long projectId,
            @PathVariable Long adminId
    ) {
        try {
            return ResponseEntity.ok(service.appsByProjectAndOwner(projectId, adminId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Server error"));
        }
    }
}