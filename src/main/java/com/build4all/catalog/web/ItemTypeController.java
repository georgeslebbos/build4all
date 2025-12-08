package com.build4all.catalog.web;

import com.build4all.catalog.dto.ItemTypeDTO;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.catalog.service.ItemTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/item-types")
public class ItemTypeController {

    private final ItemTypeRepository itemTypeRepository;
    private final ItemTypeService itemTypeService;

    public ItemTypeController(ItemTypeRepository itemTypeRepository,
                              ItemTypeService itemTypeService) {
        this.itemTypeRepository = itemTypeRepository;
        this.itemTypeService = itemTypeService;
    }

    // ---------- Payload ----------

    public static class ItemTypePayload {
        private String name;
        private String icon;
        private String iconLibrary;
        private Long categoryId; // required for create; optional for update

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

    // ---------- GET: raw entities (mainly backend/admin use) ----------

    @Operation(summary = "List all item types (entities)")
    @ApiResponse(responseCode = "200", description = "Successful")
    @GetMapping
    public List<ItemType> getAll() {
        return itemTypeRepository.findAll();
    }

    // ---------- DELETE ----------

    @Operation(summary = "Delete an item type")
    @ApiResponse(responseCode = "200", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!itemTypeRepository.existsById(id)) {
            return error(HttpStatus.NOT_FOUND, "Item type not found");
        }
        itemTypeService.deleteItemType(id);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Item type deleted");
        return ResponseEntity.ok(body);
    }

    // ---------- CREATE ----------

    @Operation(summary = "Create an item type (admin)")
    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody ItemTypePayload body) {
        try {
            if (body.getCategoryId() == null) {
                return badRequest("categoryId is required");
            }
            if (body.getName() == null || body.getName().isBlank()) {
                return badRequest("name is required");
            }

            ItemType saved = itemTypeService.createItemType(
                    body.getName(),
                    body.getIcon(),
                    body.getIconLibrary(),
                    body.getCategoryId()
            );

            ItemTypeDTO dto = toDto(saved);
            return ResponseEntity
                    .created(URI.create("/api/item-types/" + saved.getId()))
                    .body(dto);
        } catch (IllegalArgumentException ex) {
            // أي validation / business error من الـ service نرجعه كـ JSON
            return badRequest(ex.getMessage());
        }
    }

    // ---------- UPDATE ----------

    @Operation(summary = "Update an item type (admin)")
    @ApiResponse(responseCode = "200", description = "Updated")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody ItemTypePayload body) {
        try {
            ItemType updated = itemTypeService.updateItemType(
                    id,
                    body.getName(),
                    body.getIcon(),
                    body.getIconLibrary(),
                    body.getCategoryId()
            );
            ItemTypeDTO dto = toDto(updated);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    // ---------- LIST BY PROJECT ----------

    @Operation(summary = "List item types for a project (via category.project)")
    @GetMapping("/by-project/{projectId}")
    public List<ItemTypeDTO> getByProject(@PathVariable Long projectId) {
        return itemTypeService.listByProject(projectId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ---------- LIST BY CATEGORY ----------

    @Operation(summary = "List item types for a category")
    @GetMapping("/by-category/{categoryId}")
    public List<ItemTypeDTO> getByCategory(@PathVariable Long categoryId) {
        return itemTypeService.listByCategory(categoryId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ---------- DTO mapper ----------

    private ItemTypeDTO toDto(ItemType type) {
        Category cat = type.getCategory();
        Long categoryId = (cat != null) ? cat.getId() : null;
        return new ItemTypeDTO(
                type.getId(),
                type.getName(),
                type.getName(),      // displayName == name (adjust if needed)
                type.getIcon(),
                type.getIconLibrary(),
                categoryId
        );
    }
}
