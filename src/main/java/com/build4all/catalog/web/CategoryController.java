package com.build4all.catalog.web;

import com.build4all.catalog.dto.CategoryDTO;
import com.build4all.catalog.dto.CategoryRequest;
import com.build4all.catalog.domain.Icon;
import com.build4all.project.domain.Project;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.IconRepository;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.catalog.repository.CategoryRepository;
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

    public CategoryController(CategoryRepository categoryRepo,
                              IconRepository iconRepo,
                              ProjectRepository projectRepo) {
        this.categoryRepo = categoryRepo;
        this.iconRepo = iconRepo;
        this.projectRepo = projectRepo;
    }

    /* ------------ DTO helpers ------------ */

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

    /* ------------ READ ------------ */

    @GetMapping
    public List<CategoryDTO> list() {
        return categoryRepo.findAllByOrderByNameAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/by-project/{projectId}")
    public List<CategoryDTO> listByProject(@PathVariable Long projectId) {
        return categoryRepo.findByProject_IdOrderByNameAsc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> get(@PathVariable Long id) {
        return categoryRepo.findById(id)
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /* ------------ CREATE ------------ */

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CategoryRequest req,
                                    @RequestParam(defaultValue = "true") boolean ensureIconExists) {
        if (req.getProjectId() == null) {
            return badRequest("projectId is required");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            return badRequest("name is required");
        }

        Project project = projectRepo.findById(req.getProjectId()).orElse(null);
        if (project == null) {
            return badRequest("project not found: " + req.getProjectId());
        }

        String name = req.getName().trim().toUpperCase();
        String iconName = req.getIconName() == null ? null : req.getIconName().trim();
        String iconLib = (req.getIconLibrary() == null || req.getIconLibrary().isBlank())
                ? "Ionicons" : req.getIconLibrary().trim();

        if (categoryRepo.existsByNameIgnoreCaseAndProject_Id(name, project.getId())) {
            return error(HttpStatus.CONFLICT,
                    "Category already exists in this project: " + name);
        }

        if (ensureIconExists && iconName != null && !iconName.isBlank()) {
            Optional<Icon> icOpt = iconRepo.findByNameIgnoreCase(iconName);
            if (icOpt.isEmpty()) {
                iconRepo.save(new Icon(iconName, iconLib));
            } else {
                Icon ic = icOpt.get();
                if (ic.getLibrary() == null || !ic.getLibrary().equalsIgnoreCase(iconLib)) {
                    ic.setLibrary(iconLib);
                    iconRepo.save(ic);
                }
            }
        }

        Category entity = new Category();
        entity.setProject(project);
        entity.setName(name);
        entity.setIconName(iconName);
        entity.setIconLibrary(iconLib);

        Category saved = categoryRepo.save(entity);

        CategoryDTO body = toDto(saved);
        return ResponseEntity
                .created(URI.create("/api/admin/categories/" + saved.getId()))
                .body(body);
    }

    @PostMapping("/batch")
    public ResponseEntity<?> createBatch(@RequestBody List<CategoryRequest> list,
                                         @RequestParam(defaultValue = "true") boolean ensureIconExists) {
        if (list == null || list.isEmpty()) {
            return badRequest("payload is empty");
        }

        Optional<CategoryRequest> missing = list.stream()
                .filter(r -> r.getProjectId() == null || r.getName() == null || r.getName().isBlank())
                .findFirst();
        if (missing.isPresent()) {
            return badRequest("each item requires projectId and name");
        }

        List<CategoryDTO> saved = list.stream().map(req -> {
            Project project = projectRepo.findById(req.getProjectId()).orElse(null);
            if (project == null) return null;

            String name = req.getName().trim().toUpperCase();

            if (categoryRepo.existsByNameIgnoreCaseAndProject_Id(name, project.getId())) {
                return categoryRepo.findByNameIgnoreCaseAndProject_Id(name, project.getId())
                        .map(this::toDto).orElse(null);
            }

            String iconName = req.getIconName() == null ? null : req.getIconName().trim();
            String iconLib  = (req.getIconLibrary() == null || req.getIconLibrary().isBlank())
                    ? "Ionicons" : req.getIconLibrary().trim();

            if (ensureIconExists && iconName != null && !iconName.isBlank()) {
                Optional<Icon> ic = iconRepo.findByNameIgnoreCase(iconName);
                if (ic.isEmpty()) {
                    iconRepo.save(new Icon(iconName, iconLib));
                } else if (ic.get().getLibrary() == null || !ic.get().getLibrary().equalsIgnoreCase(iconLib)) {
                    Icon toUpdate = ic.get();
                    toUpdate.setLibrary(iconLib);
                    iconRepo.save(toUpdate);
                }
            }

            Category entity = new Category();
            entity.setProject(project);
            entity.setName(name);
            entity.setIconName(iconName);
            entity.setIconLibrary(iconLib);

            return toDto(categoryRepo.save(entity));
        }).filter(Objects::nonNull).collect(Collectors.toList());

        return ResponseEntity.ok(saved);
    }

    /* ------------ UPDATE ------------ */

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody CategoryRequest req,
                                    @RequestParam(defaultValue = "true") boolean ensureIconExists) {
        Optional<Category> opt = categoryRepo.findById(id);
        if (opt.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "Category not found");
        }
        Category existing = opt.get();

        if (req.getProjectId() != null && !req.getProjectId().equals(
                existing.getProject() != null ? existing.getProject().getId() : null)) {
            Project project = projectRepo.findById(req.getProjectId()).orElse(null);
            if (project == null) {
                return badRequest("project not found: " + req.getProjectId());
            }
            existing.setProject(project);
        }

        if (req.getName() != null && !req.getName().isBlank()) {
            String newName = req.getName().trim().toUpperCase();
            Long projId = existing.getProject() != null ? existing.getProject().getId() : null;
            if (projId != null
                    && !existing.getName().equalsIgnoreCase(newName)
                    && categoryRepo.existsByNameIgnoreCaseAndProject_Id(newName, projId)) {
                return error(HttpStatus.CONFLICT,
                        "Category already exists in this project: " + newName);
            }
            existing.setName(newName);
        }

        if (req.getIconName() != null) {
            String newIcon = req.getIconName().trim();
            existing.setIconName(newIcon);

            if (ensureIconExists && !newIcon.isBlank()) {
                Optional<Icon> ic = iconRepo.findByNameIgnoreCase(newIcon);
                if (ic.isEmpty()) {
                    String lib = req.getIconLibrary() != null && !req.getIconLibrary().isBlank()
                            ? req.getIconLibrary().trim()
                            : (existing.getIconLibrary() != null ? existing.getIconLibrary() : "Ionicons");
                    iconRepo.save(new Icon(newIcon, lib));
                }
            }
        }

        if (req.getIconLibrary() != null && !req.getIconLibrary().isBlank()) {
            String newLib = req.getIconLibrary().trim();
            existing.setIconLibrary(newLib);

            if (ensureIconExists && existing.getIconName() != null && !existing.getIconName().isBlank()) {
                Optional<Icon> ic = iconRepo.findByNameIgnoreCase(existing.getIconName());
                ic.ifPresent(icon -> {
                    if (icon.getLibrary() == null || !icon.getLibrary().equalsIgnoreCase(newLib)) {
                        icon.setLibrary(newLib);
                        iconRepo.save(icon);
                    }
                });
            }
        }

        Category saved = categoryRepo.save(existing);
        return ResponseEntity.ok(toDto(saved));
    }

    /* ------------ DELETE ------------ */

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!categoryRepo.existsById(id)) {
            return error(HttpStatus.NOT_FOUND, "Category not found");
        }
        categoryRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
