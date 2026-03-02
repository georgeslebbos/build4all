// src/main/java/com/build4all/catalog/web/ItemTypeController.java
package com.build4all.catalog.web;

import com.build4all.catalog.dto.ItemTypeDTO;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.service.DeleteBlockedException;
import com.build4all.catalog.service.ItemTypeService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/item-types")
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','USER')")
public class ItemTypeController {

    private static final Logger log = LoggerFactory.getLogger(ItemTypeController.class);

    private final ItemTypeService itemTypeService;
    private final JwtUtil jwtUtil;

    public ItemTypeController(ItemTypeService itemTypeService, JwtUtil jwtUtil) {
        this.itemTypeService = itemTypeService;
        this.jwtUtil = jwtUtil;
    }

    // ---------- Payload ----------
    public static class ItemTypePayload {
        private String name;
        private String icon;
        private String iconLibrary;
        private Long categoryId;

        public String getName() { return name; }
        public String getIcon() { return icon; }
        public String getIconLibrary() { return iconLibrary; }
        public Long getCategoryId() { return categoryId; }

        public void setName(String name) { this.name = name; }
        public void setIcon(String icon) { this.icon = icon; }
        public void setIconLibrary(String iconLibrary) { this.iconLibrary = iconLibrary; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    }

    // ---------- Common error helpers ----------
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
     * Uses your JwtUtil.requireOwnerProjectId(...) which throws RuntimeException on invalid token / missing claim.
     */
    private Long tenantFromAuth(String authHeader) {
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    /**
     * ✅ Consistent mapping of auth failures.
     * - missing/invalid token -> 401
     * - explicit forbidden/mismatch -> 403
     */
    private ResponseEntity<Map<String, Object>> authFail(String msg) {
        String m = (msg == null) ? "" : msg.toLowerCase();
        if (m.contains("forbidden") || m.contains("mismatch")) {
            return forbidden("Forbidden");
        }
        return unauthorized((msg == null || msg.isBlank()) ? "Unauthorized" : msg);
    }

    // ---------- GET (tenant-safe) ----------
    @Operation(summary = "List all item types for current tenant")
    @ApiResponse(responseCode = "200", description = "Successful")
    @GetMapping
    public ResponseEntity<?> getAllForTenant(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            List<ItemTypeDTO> out = itemTypeService.listAllForOwnerProject(ownerProjectId)
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(out);

        } catch (RuntimeException e) {
            return authFail(e.getMessage());
        } catch (Exception e) {
            log.error("getAllForTenant failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load item types");
        }
    }

    // ---------- CREATE (tenant-safe) ----------
    @Operation(summary = "Create an item type (tenant-safe)")
    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody ItemTypePayload body,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            if (body.getCategoryId() == null) return badRequest("categoryId is required");
            if (body.getName() == null || body.getName().isBlank()) return badRequest("name is required");

            Long ownerProjectId = tenantFromAuth(auth);

            ItemType saved = itemTypeService.createItemType(
                    body.getName(),
                    body.getIcon(),
                    body.getIconLibrary(),
                    body.getCategoryId(),
                    ownerProjectId
            );

            return ResponseEntity
                    .created(URI.create("/api/item-types/" + saved.getId()))
                    .body(toDto(saved));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();

            // auth-ish problems
            if (msg.contains("token") || msg.contains("unauthorized") || msg.contains("ownerprojectid")
                    || msg.contains("mismatch") || msg.contains("forbidden")) {
                return authFail(e.getMessage());
            }

            // category not found / cross tenant => hide existence
            if (msg.contains("category") && msg.contains("not found")) {
                return error(HttpStatus.NOT_FOUND, "Category not found: " + body.getCategoryId());
            }

            // duplicate name => 409
            if (msg.contains("already exists") || msg.contains("duplicate")) {
                return error(HttpStatus.CONFLICT, e.getMessage());
            }

            return badRequest(e.getMessage());

        } catch (Exception e) {
            log.error("create item type failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create item type");
        }
    }

    // ---------- UPDATE (tenant-safe) ----------
    @Operation(summary = "Update an item type (tenant-safe)")
    @ApiResponse(responseCode = "200", description = "Updated")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody ItemTypePayload body,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            ItemType updated = itemTypeService.updateItemType(
                    id,
                    body.getName(),
                    body.getIcon(),
                    body.getIconLibrary(),
                    body.getCategoryId(),
                    ownerProjectId
            );

            return ResponseEntity.ok(toDto(updated));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();

            if (msg.contains("token") || msg.contains("unauthorized") || msg.contains("ownerprojectid")
                    || msg.contains("mismatch") || msg.contains("forbidden")) {
                return authFail(e.getMessage());
            }

            // hide cross-tenant / not found
            return error(HttpStatus.NOT_FOUND, "ItemType not found: " + id);

        } catch (Exception e) {
            log.error("update item type failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update item type");
        }
    }

    // ---------- DELETE (tenant-safe) ----------
    @Operation(summary = "Delete an item type (tenant-safe)")
    @ApiResponse(responseCode = "200", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            itemTypeService.deleteItemType(id, ownerProjectId);
            return ResponseEntity.ok(Map.of("message", "Item type deleted"));

        } catch (DeleteBlockedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot delete item type because items are using it.",
                    "code", e.getCode(),
                    "count", e.getCount()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Cannot delete the default item type because it is the only one in this category.",
                    "code", e.getMessage()
            ));

        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();

            if (msg.contains("token") || msg.contains("unauthorized") || msg.contains("ownerprojectid")
                    || msg.contains("mismatch") || msg.contains("forbidden")) {
                return authFail(e.getMessage());
            }

            // hide cross-tenant / not found
            return error(HttpStatus.NOT_FOUND, "ItemType not found: " + id);

        } catch (Exception e) {
            log.error("delete item type failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete item type");
        }
    }

    // ---------- LIST BY PROJECT (tenant-safe) ----------
    @Operation(summary = "List item types for a project (tenant-safe)")
    @GetMapping("/by-project/{projectId}")
    public ResponseEntity<?> getByProject(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            List<ItemTypeDTO> out = itemTypeService.listByProject(projectId, ownerProjectId)
                    .stream().map(this::toDto).collect(Collectors.toList());

            return ResponseEntity.ok(out);

        } catch (RuntimeException e) {
            return authFail(e.getMessage());
        } catch (Exception e) {
            log.error("getByProject failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load item types by project");
        }
    }

    // ---------- LIST BY CATEGORY (tenant-safe) ----------
    @Operation(summary = "List item types for a category (tenant-safe)")
    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<?> getByCategory(
            @PathVariable Long categoryId,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        try {
            if (auth == null || auth.isBlank()) return unauthorized("Missing Authorization header");

            Long ownerProjectId = tenantFromAuth(auth);

            List<ItemTypeDTO> out = itemTypeService.listByCategory(categoryId, ownerProjectId)
                    .stream().map(this::toDto).collect(Collectors.toList());

            return ResponseEntity.ok(out);

        } catch (RuntimeException e) {
            return authFail(e.getMessage());
        } catch (Exception e) {
            log.error("getByCategory failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load item types by category");
        }
    }

    // ---------- DTO mapper ----------
    private ItemTypeDTO toDto(ItemType type) {
        Category cat = type.getCategory();
        Long categoryId = (cat != null) ? cat.getId() : null;

        return new ItemTypeDTO(
                type.getId(),
                type.getName(),
                type.getName(),     // if your DTO expects label/displayName, keep same
                type.getIcon(),
                type.getIconLibrary(),
                categoryId
        );
    }
}