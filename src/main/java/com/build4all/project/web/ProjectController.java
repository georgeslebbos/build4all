package com.build4all.project.web;

import com.build4all.project.domain.Project;
import com.build4all.security.JwtUtil;
import com.build4all.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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

    private boolean isAdminOrBusiness(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return false;
        String token = auth.substring(7).trim();
        return jwt.isAdminToken(token) || jwt.isBusinessToken(token);
    }

    @Operation(summary = "Create a project")
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("Authorization") String auth,
                                    @RequestBody Map<String, Object> body) {
        if (!isAdminOrBusiness(auth)) return ResponseEntity.status(403).body("Forbidden");
        try {
            String name = (String) body.get("projectName");
            String description = (String) body.get("description");
            Boolean active = body.get("active") == null ? null : Boolean.valueOf(body.get("active").toString());
            Project p = service.create(name, description, active);
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
        if (!isAdminOrBusiness(auth)) return ResponseEntity.status(403).body("Forbidden");
        try {
            String name = (String) body.get("projectName");
            String description = (String) body.get("description");
            Boolean active = body.get("active") == null ? null : Boolean.valueOf(body.get("active").toString());
            return ResponseEntity.ok(service.update(id, name, description, active));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Delete a project")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        if (!isAdminOrBusiness(auth)) return ResponseEntity.status(403).body("Forbidden");
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
