package com.build4all.catalog.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.dto.CategoryDTO;
import com.build4all.catalog.dto.CategoryRequest;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.Icon;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.IconRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/categories")
public class CategoryController {

    private final CategoryRepository categoryRepo;
    private final IconRepository iconRepo;
    private final ProjectRepository projectRepo;
    private final AdminUserProjectRepository aupRepo;

    public CategoryController(CategoryRepository categoryRepo,
                              IconRepository iconRepo,
                              ProjectRepository projectRepo,
                              AdminUserProjectRepository aupRepo) {
        this.categoryRepo = categoryRepo;
        this.iconRepo = iconRepo;
        this.projectRepo = projectRepo;
        this.aupRepo = aupRepo;
    }

    // =========================
    // Helpers
    // =========================

    private CategoryDTO toDto(Category c) {
        return new CategoryDTO(
                c.getId(),
                c.getName(),
                c.getIconName(),
                c.getIconLibrary()
        );
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String msg) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", msg);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim().toUpperCase();
    }

    private String normalizeIconLib(String iconLib) {
        return (iconLib == null || iconLib.isBlank()) ? "Ionicons" : iconLib.trim();
    }

    // ✅ Resolve PROJECT id from ownerProjectId (AdminUserProject id)
    private Long resolveProjectIdFromOwnerProject(Long ownerProjectId) {
        return aupRepo.findProjectIdByLinkId(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ownerProject not found: " + ownerProjectId));
    }

    private void ensureIconExistsIfNeeded(boolean ensureIconExists, String iconName, String iconLib) {
        if (!ensureIconExists) return;
        if (iconName == null || iconName.isBlank()) return;

        Optional<Icon> icOpt = iconRepo.findByNameIgnoreCase(iconName.trim());
        if (icOpt.isEmpty()) {
            iconRepo.save(new Icon(iconName.trim(), iconLib));
        } else {
            Icon ic = icOpt.get();
            if (ic.getLibrary() == null || !ic.getLibrary().equalsIgnoreCase(iconLib)) {
                ic.setLibrary(iconLib);
                iconRepo.save(ic);
            }
        }
    }

    // =========================
    // READ
    // =========================

    // ⚠️ ADMIN ONLY: lists ALL categories in DB (all owners/projects)
    @GetMapping
    public List<CategoryDTO> listAll() {
        return categoryRepo.findAllByOrderByNameAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ⚠️ PROJECT SCOPE (shared across apps under same project)
    @GetMapping("/by-project/{projectId}")
    public List<CategoryDTO> listByProject(@PathVariable Long projectId) {
        return categoryRepo.findByProject_IdOrderByNameAsc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ✅ OWNER PROJECT SCOPE (TENANT): app1/app2/app3 separated
    @GetMapping("/by-owner-project/{ownerProjectId}")
    public ResponseEntity<?> listByOwnerProject(@PathVariable Long ownerProjectId) {
        try {
            // optional validation that ownerProject exists:
            resolveProjectIdFromOwnerProject(ownerProjectId);

            List<CategoryDTO> out = categoryRepo.findByOwnerProjectIdOrderByNameAsc(ownerProjectId)
                    .stream()
                    .map(this::toDto)
                    .toList();

            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load categories by owner project");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> get(@PathVariable Long id) {
        return categoryRepo.findById(id)
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================
    // CREATE
    // =========================

    // ⚠️ PROJECT-SCOPED create (shared across apps)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CategoryRequest req,
                                    @RequestParam(defaultValue = "true") boolean ensureIconExists) {
        try {
            if (req.getProjectId() == null) return badRequest("projectId is required");
            if (req.getName() == null || req.getName().isBlank()) return badRequest("name is required");

            Project project = projectRepo.findById(req.getProjectId()).orElse(null);
            if (project == null) return badRequest("project not found: " + req.getProjectId());

            String name = normalizeName(req.getName());
            String iconName = (req.getIconName() == null) ? null : req.getIconName().trim();
            String iconLib = normalizeIconLib(req.getIconLibrary());

            // project-scoped uniqueness
            if (categoryRepo.existsByNameIgnoreCaseAndProject_Id(name, project.getId())) {
                return error(HttpStatus.CONFLICT, "Category already exists in this project: " + name);
            }

            ensureIconExistsIfNeeded(ensureIconExists, iconName, iconLib);

            Category entity = new Category();
            entity.setProject(project);
            entity.setName(name);
            entity.setIconName(iconName);
            entity.setIconLibrary(iconLib);

            // IMPORTANT: project-scoped create means tenant is null
            entity.setOwnerProjectId(null);

            Category saved = categoryRepo.save(entity);

            return ResponseEntity
                    .created(URI.create("/api/admin/categories/" + saved.getId()))
                    .body(toDto(saved));
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create category");
        }
    }

    // ✅ TENANT-SCOPED create (ownerProjectId)
    @PostMapping("/by-owner-project/{ownerProjectId}")
    public ResponseEntity<?> createByOwnerProject(@PathVariable Long ownerProjectId,
                                                 @RequestBody CategoryRequest req,
                                                 @RequestParam(defaultValue = "true") boolean ensureIconExists) {
        try {
            if (req.getName() == null || req.getName().isBlank()) return badRequest("name is required");

            Long projectId = resolveProjectIdFromOwnerProject(ownerProjectId);
            Project projectRef = projectRepo.getReferenceById(projectId);

            String name = normalizeName(req.getName());
            String iconName = (req.getIconName() == null) ? null : req.getIconName().trim();
            String iconLib = normalizeIconLib(req.getIconLibrary());

            // ✅ ownerProject-scoped uniqueness
            if (categoryRepo.existsByNameIgnoreCaseAndOwnerProjectId(name, ownerProjectId)) {
                return error(HttpStatus.CONFLICT, "Category already exists in this owner project: " + name);
            }

            ensureIconExistsIfNeeded(ensureIconExists, iconName, iconLib);

            Category entity = new Category();
            entity.setProject(projectRef);               // still required (FK not null)
            entity.setOwnerProjectId(ownerProjectId);    // ✅ THIS is the tenant scope
            entity.setName(name);
            entity.setIconName(iconName);
            entity.setIconLibrary(iconLib);

            Category saved = categoryRepo.save(entity);

            return ResponseEntity
                    .created(URI.create("/api/admin/categories/" + saved.getId()))
                    .body(toDto(saved));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create category by owner project");
        }
    }

    // =========================
    // UPDATE
    // =========================

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody CategoryRequest req,
                                    @RequestParam(defaultValue = "true") boolean ensureIconExists) {
        try {
            Optional<Category> opt = categoryRepo.findById(id);
            if (opt.isEmpty()) return error(HttpStatus.NOT_FOUND, "Category not found");

            Category existing = opt.get();

            // If name update -> enforce uniqueness in SAME scope:
            // - if ownerProjectId is set -> uniqueness per ownerProject
            // - else -> uniqueness per project
            if (req.getName() != null && !req.getName().isBlank()) {
                String newName = normalizeName(req.getName());

                Long ownerProjectId = existing.getOwnerProjectId();
                Long projectId = existing.getProject() != null ? existing.getProject().getId() : null;

                boolean changed = existing.getName() == null || !existing.getName().equalsIgnoreCase(newName);

                if (changed) {
                    if (ownerProjectId != null) {
                        if (categoryRepo.existsByNameIgnoreCaseAndOwnerProjectId(newName, ownerProjectId)) {
                            return error(HttpStatus.CONFLICT, "Category already exists in this owner project: " + newName);
                        }
                    } else if (projectId != null) {
                        if (categoryRepo.existsByNameIgnoreCaseAndProject_Id(newName, projectId)) {
                            return error(HttpStatus.CONFLICT, "Category already exists in this project: " + newName);
                        }
                    }
                }

                existing.setName(newName);
            }

            // Icon name update
            if (req.getIconName() != null) {
                String newIcon = req.getIconName().trim();
                existing.setIconName(newIcon);

                if (ensureIconExists && !newIcon.isBlank()) {
                    String lib = normalizeIconLib(
                            req.getIconLibrary() != null ? req.getIconLibrary() : existing.getIconLibrary()
                    );
                    ensureIconExistsIfNeeded(true, newIcon, lib);
                }
            }

            // Icon library update
            if (req.getIconLibrary() != null && !req.getIconLibrary().isBlank()) {
                String newLib = req.getIconLibrary().trim();
                existing.setIconLibrary(newLib);

                if (ensureIconExists && existing.getIconName() != null && !existing.getIconName().isBlank()) {
                    ensureIconExistsIfNeeded(true, existing.getIconName(), newLib);
                }
            }

            Category saved = categoryRepo.save(existing);
            return ResponseEntity.ok(toDto(saved));
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update category");
        }
    }

    // =========================
    // DELETE
    // =========================

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            if (!categoryRepo.existsById(id)) return error(HttpStatus.NOT_FOUND, "Category not found");
            categoryRepo.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete category");
        }
    }
}
