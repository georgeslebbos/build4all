// com/build4all/catalog/web/ItemTypeController.java
package com.build4all.catalog.web;

import com.build4all.catalog.dto.ItemTypeDTO;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.catalog.service.ItemTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
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

    // ---------- GET: raw entities ----------
    @Operation(summary = "List all item types (entities)")
    @ApiResponse(responseCode = "200", description = "Successful")
    @GetMapping
    public List<ItemType> getAll() {
        return itemTypeRepository.findAll();
    }

    // ---------- DELETE ----------
    @Operation(summary = "Delete an item type")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!itemTypeRepository.existsById(id)) return ResponseEntity.notFound().build();
        itemTypeService.deleteItemType(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- CREATE ----------
    @Operation(summary = "Create an item type (admin)")
    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping
    public ResponseEntity<ItemType> create(@RequestBody ItemTypePayload body) {
        ItemType saved = itemTypeService.createItemType(
                body.getName(),
                body.getIcon(),
                body.getIconLibrary(),
                body.getCategoryId()
        );
        return ResponseEntity
                .created(URI.create("/api/item-types/" + saved.getId()))
                .body(saved);
    }

    // ---------- UPDATE ----------
    @Operation(summary = "Update an item type (admin)")
    @ApiResponse(responseCode = "200", description = "Updated")
    @PutMapping("/{id}")
    public ResponseEntity<ItemType> update(@PathVariable Long id,
                                           @RequestBody ItemTypePayload body) {
        ItemType updated = itemTypeService.updateItemType(
                id,
                body.getName(),
                body.getIcon(),
                body.getIconLibrary(),
                body.getCategoryId()
        );
        return ResponseEntity.ok(updated);
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
