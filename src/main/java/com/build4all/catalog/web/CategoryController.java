// src/main/java/com/build4all/catalog/web/CategoryController.java
package com.build4all.catalog.web;

import com.build4all.catalog.domain.Category;
import com.build4all.catalog.dto.CategoryDTO;
import com.build4all.catalog.dto.CategoryRequest;
import com.build4all.catalog.service.CategoryService;
import com.build4all.catalog.service.DeleteBlockedException;
import com.build4all.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','USER')")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;
    private final JwtUtil jwtUtil;

    public CategoryController(CategoryService categoryService, JwtUtil jwtUtil) {
        this.categoryService = categoryService;
        this.jwtUtil = jwtUtil;
    }

    // ---------- DTO ----------
    private CategoryDTO toDto(Category c) {
        return new CategoryDTO(
                c.getId(),
                c.getName(),
                c.getIconName(),
                c.getIconLibrary()
        );
    }

    // ---------- Errors ----------
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String msg) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", msg);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String msg) {
        return error(HttpStatus.UNAUTHORIZED, msg);
    }

    private ResponseEntity<Map<String, Object>> forbidden(String msg) {
        return error(HttpStatus.FORBIDDEN, msg);
    }

    /**
     * ✅ Tenant always from token (multi-tenant safe)
     */
    private Long tenantFromAuth(String authHeader) {
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    /**
     * ✅ Map JWT errors into 401/403 consistently.
     */
    private ResponseEntity<Map<String, Object>> authFail(String msg) {
        String m = (msg == null) ? "" : msg;

        if (m.toLowerCase().contains("forbidden") || m.toLowerCase().contains("mismatch")) {
            return forbidden("Forbidden");
        }
        return unauthorized(m.isBlank() ? "Unauthorized" : m);
    }

    // ---------- READ (tenant-safe) ----------
    @GetMapping
    public ResponseEntity<?> listAllForTenant(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            List<CategoryDTO> out = categoryService.listAllForOwnerProject(ownerProjectId)
                    .stream().map(this::toDto).toList();

            return ResponseEntity.ok(out);

        } catch (RuntimeException e) {
            return authFail(e.getMessage());
        } catch (Exception e) {
            log.error("list categories failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load categories");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getForTenant(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            Category c = categoryService.getForTenant(id, ownerProjectId);
            return ResponseEntity.ok(toDto(c));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.toLowerCase().contains("token")
                    || msg.toLowerCase().contains("unauthorized")
                    || msg.toLowerCase().contains("ownerprojectid")
                    || msg.toLowerCase().contains("mismatch")
                    || msg.toLowerCase().contains("forbidden")) {
                return authFail(msg);
            }
            return error(HttpStatus.NOT_FOUND, "Category not found: " + id);
        } catch (Exception e) {
            log.error("get category failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load category");
        }
    }

    // ---------- LIST BY PROJECT (tenant-safe) ----------
    @GetMapping("/by-project/{projectId}")
    public ResponseEntity<?> listByProjectForTenant(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            List<CategoryDTO> out = categoryService.listByProjectForTenant(projectId, ownerProjectId)
                    .stream().map(this::toDto).toList();

            return ResponseEntity.ok(out);

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.toLowerCase().contains("project") || msg.toLowerCase().contains("not found")) {
                return error(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
            }
            return authFail(msg);
        } catch (Exception e) {
            log.error("listByProject failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load categories by project");
        }
    }

    // ---------- CREATE (tenant-safe) ----------
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    @PostMapping
    public ResponseEntity<?> createForTenant(
            @RequestBody CategoryRequest req,
            @RequestParam(defaultValue = "true") boolean ensureIconExists,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            Category saved = categoryService.createForTenant(ownerProjectId, req, ensureIconExists);

            return ResponseEntity
                    .created(URI.create("/api/admin/categories/" + saved.getId()))
                    .body(toDto(saved));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();

            if (msg.toLowerCase().contains("token")
                    || msg.toLowerCase().contains("unauthorized")
                    || msg.toLowerCase().contains("ownerprojectid")
                    || msg.toLowerCase().contains("mismatch")
                    || msg.toLowerCase().contains("forbidden")) {
                return authFail(msg);
            }

            if (msg.toLowerCase().contains("already exists")) {
                return error(HttpStatus.CONFLICT, msg);
            }

            return badRequest(msg.isBlank() ? "Bad request" : msg);

        } catch (Exception e) {
            log.error("create category failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create category");
        }
    }

    // ---------- UPDATE (tenant-safe) ----------

    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateForTenant(
            @PathVariable Long id,
            @RequestBody CategoryRequest req,
            @RequestParam(defaultValue = "true") boolean ensureIconExists,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            Category saved = categoryService.updateForTenant(id, ownerProjectId, req, ensureIconExists);
            return ResponseEntity.ok(toDto(saved));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();

            if (msg.toLowerCase().contains("token")
                    || msg.toLowerCase().contains("unauthorized")
                    || msg.toLowerCase().contains("ownerprojectid")
                    || msg.toLowerCase().contains("mismatch")
                    || msg.toLowerCase().contains("forbidden")) {
                return authFail(msg);
            }

            if (msg.toLowerCase().contains("already exists")) {
                return error(HttpStatus.CONFLICT, msg);
            }

            return error(HttpStatus.NOT_FOUND, "Category not found: " + id);

        } catch (Exception e) {
            log.error("update category failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update category");
        }
    }

    // ---------- DELETE (tenant-safe) ----------
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteForTenant(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            CategoryService.DeleteCascadeResult res = categoryService.deleteCategoryCascade(id, ownerProjectId);

            return ResponseEntity.ok(Map.of(
                    "message", "Category deleted",
                    "deletedItemTypes", res.deletedItemTypes()
            ));

        } catch (DeleteBlockedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot delete category because it is still used.",
                    "code", e.getCode(),
                    "count", e.getCount()
            ));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();

            if (msg.toLowerCase().contains("token")
                    || msg.toLowerCase().contains("unauthorized")
                    || msg.toLowerCase().contains("ownerprojectid")
                    || msg.toLowerCase().contains("mismatch")
                    || msg.toLowerCase().contains("forbidden")) {
                return authFail(msg);
            }

            return error(HttpStatus.NOT_FOUND, "Category not found: " + id);

        } catch (Exception e) {
            log.error("delete category failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete category");
        }
    }

    // ---------------- BACKWARD COMPAT ENDPOINTS ----------------
    // Keeps your old routes alive but now they are tenant-verified (no spoofing).

    @GetMapping("/by-owner-project/{ownerProjectId}")
    public ResponseEntity<?> listByOwnerProjectLegacy(
            @PathVariable Long ownerProjectId,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long tokenTenant = tenantFromAuth(auth);

            if (!tokenTenant.equals(ownerProjectId)) {
                return error(HttpStatus.NOT_FOUND, "Categories not found");
            }

            List<CategoryDTO> out = categoryService.listAllForOwnerProject(tokenTenant)
                    .stream().map(this::toDto).toList();

            return ResponseEntity.ok(out);

        } catch (RuntimeException e) {
            return authFail(e.getMessage());
        } catch (Exception e) {
            log.error("listByOwnerProject legacy failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load categories");
        }
    }

    @PostMapping("/by-owner-project/{ownerProjectId}")
    public ResponseEntity<?> createByOwnerProjectLegacy(
            @PathVariable Long ownerProjectId,
            @RequestBody CategoryRequest req,
            @RequestParam(defaultValue = "true") boolean ensureIconExists,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long tokenTenant = tenantFromAuth(auth);

            if (!tokenTenant.equals(ownerProjectId)) {
                return error(HttpStatus.NOT_FOUND, "Categories not found");
            }

            Category saved = categoryService.createForTenant(tokenTenant, req, ensureIconExists);

            return ResponseEntity
                    .created(URI.create("/api/admin/categories/" + saved.getId()))
                    .body(toDto(saved));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();

            if (msg.toLowerCase().contains("already exists")) return error(HttpStatus.CONFLICT, msg);

            if (msg.toLowerCase().contains("token")
                    || msg.toLowerCase().contains("unauthorized")
                    || msg.toLowerCase().contains("ownerprojectid")
                    || msg.toLowerCase().contains("mismatch")
                    || msg.toLowerCase().contains("forbidden")) {
                return authFail(msg);
            }

            return badRequest(msg.isBlank() ? "Bad request" : msg);

        } catch (Exception e) {
            log.error("create legacy failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create category");
        }
    }
}