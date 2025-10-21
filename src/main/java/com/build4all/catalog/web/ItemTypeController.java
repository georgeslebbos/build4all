package com.build4all.catalog.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
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
    private final AdminUserProjectRepository aupRepo;

    public ItemTypeController(ItemTypeRepository itemTypeRepository,
                              ItemTypeService itemTypeService,
                              AdminUserProjectRepository aupRepo) {
        this.itemTypeRepository = itemTypeRepository;
        this.itemTypeService = itemTypeService;
        this.aupRepo = aupRepo;
    }

    // ---------- Payload ----------
    public static class ItemTypePayload {
        private String name;
        private String icon;
        private String iconLibrary;
        private Long categoryId; // required for create; optional for update
        private Long aupId;      // optional owner link id

        public String getName() { return name; }
        public String getIcon() { return icon; }
        public String getIconLibrary() { return iconLibrary; }
        public Long getCategoryId() { return categoryId; }
        public Long getAupId() { return aupId; }

        public void setName(String name) { this.name = name; }
        public void setIcon(String icon) { this.icon = icon; }
        public void setIconLibrary(String iconLibrary) { this.iconLibrary = iconLibrary; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public void setAupId(Long aupId) { this.aupId = aupId; }
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

        // optional owner link attach
        if (body.getAupId() != null) {
            aupRepo.findById(body.getAupId()).ifPresent(saved::setOwnerProject);
            saved = itemTypeRepository.save(saved);
        }

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

        // set/clear owner link if provided
        if (body.getAupId() != null) {
            if (body.getAupId() <= 0) {
                updated.setOwnerProject(null);
            } else {
                aupRepo.findById(body.getAupId()).ifPresent(updated::setOwnerProject);
            }
            updated = itemTypeRepository.save(updated);
        }

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

    // ---------- NEW: LIST BY OWNER (adminId + projectId) ----------
    @Operation(summary = "List item types by owner (adminId + projectId)")
    @GetMapping("/by-owner")
    public ResponseEntity<?> getByOwner(@RequestParam Long adminId,
                                        @RequestParam Long projectId) {
        return aupRepo.findByAdmin_AdminIdAndProject_Id(adminId, projectId)
                .<ResponseEntity<?>>map(link ->
                        ResponseEntity.ok(
                                itemTypeService.listByOwnerLink(link.getId()).stream()
                                        .map(this::toDto)
                                        .collect(Collectors.toList())
                        )
                ).orElseGet(() -> ResponseEntity.badRequest().body("Owner-project link not found"));
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
