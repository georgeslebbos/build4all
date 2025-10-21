package com.build4all.catalog.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.dto.CategoryRequest;
import com.build4all.catalog.domain.Icon;
import com.build4all.project.domain.Project;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.IconRepository;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.catalog.repository.CategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    @GetMapping
    public List<Category> list() {
        return categoryRepo.findAllByOrderByNameAsc();
    }

    @GetMapping("/by-project/{projectId}")
    public List<Category> listByProject(@PathVariable Long projectId) {
        return categoryRepo.findByProject_IdOrderByNameAsc(projectId);
    }

    // NEW: list by owner (adminId + projectId) -> resolves aup_id then filters
    @GetMapping("/by-owner")
    public ResponseEntity<?> listByOwner(@RequestParam Long adminId,
                                         @RequestParam Long projectId) {
        Optional<AdminUserProject> link = aupRepo.findByAdmin_AdminIdAndProject_Id(adminId, projectId);
        if (link.isEmpty()) return ResponseEntity.badRequest().body("Owner-project link not found");
        return ResponseEntity.ok(categoryRepo.findByOwnerProject_IdOrderByNameAsc(link.get().getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> get(@PathVariable Long id) {
        return categoryRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CategoryRequest req,
                                    @RequestParam(defaultValue = "true") boolean ensureIconExists,
                                    @RequestParam(required = false) Long aupId // optional owner link id
    ) {
        if (req.getProjectId() == null) return ResponseEntity.badRequest().body("projectId is required");
        if (req.getName() == null || req.getName().isBlank()) return ResponseEntity.badRequest().body("name is required");

        Project project = projectRepo.findById(req.getProjectId()).orElse(null);
        if (project == null) return ResponseEntity.badRequest().body("project not found: " + req.getProjectId());

        String name = req.getName().trim().toUpperCase();
        String iconName = req.getIconName() == null ? null : req.getIconName().trim();
        String iconLib = (req.getIconLibrary() == null || req.getIconLibrary().isBlank())
                ? "Ionicons" : req.getIconLibrary().trim();

        if (categoryRepo.existsByNameIgnoreCaseAndProject_Id(name, project.getId())) {
            return ResponseEntity.status(409).body("Category already exists in this project: " + name);
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

        // optional link to owner for filtering
        if (aupId != null) {
            aupRepo.findById(aupId).ifPresent(entity::setOwnerProject);
        }

        Category saved = categoryRepo.save(entity);
        return ResponseEntity.created(URI.create("/api/admin/categories/" + saved.getId())).body(saved);
    }

    @PostMapping("/batch")
    public ResponseEntity<?> createBatch(@RequestBody List<CategoryRequest> list,
                                         @RequestParam(defaultValue = "true") boolean ensureIconExists,
                                         @RequestParam(required = false) Long aupId) {
        if (list == null || list.isEmpty()) return ResponseEntity.badRequest().body("payload is empty");

        Optional<CategoryRequest> missing = list.stream()
                .filter(r -> r.getProjectId() == null || r.getName() == null || r.getName().isBlank())
                .findFirst();
        if (missing.isPresent()) return ResponseEntity.badRequest().body("each item requires projectId and name");

        AdminUserProject ownerLink = null;
        if (aupId != null) {
            ownerLink = aupRepo.findById(aupId).orElse(null);
        }

        AdminUserProject finalOwnerLink = ownerLink;
        List<Category> saved = list.stream().map(req -> {
            Project project = projectRepo.findById(req.getProjectId()).orElse(null);
            if (project == null) return null;

            String name = req.getName().trim().toUpperCase();
            if (categoryRepo.existsByNameIgnoreCaseAndProject_Id(name, project.getId())) {
                return categoryRepo.findByNameIgnoreCaseAndProject_Id(name, project.getId()).orElse(null);
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
            if (finalOwnerLink != null) entity.setOwnerProject(finalOwnerLink);

            return categoryRepo.save(entity);
        }).filter(Objects::nonNull).collect(Collectors.toList());

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody CategoryRequest req,
                                    @RequestParam(defaultValue = "true") boolean ensureIconExists,
                                    @RequestParam(required = false) Long aupId) {
        Optional<Category> opt = categoryRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Category existing = opt.get();

        if (req.getProjectId() != null && !req.getProjectId().equals(
                existing.getProject() != null ? existing.getProject().getId() : null)) {
            Project project = projectRepo.findById(req.getProjectId()).orElse(null);
            if (project == null) return ResponseEntity.badRequest().body("project not found: " + req.getProjectId());
            existing.setProject(project);
        }

        if (req.getName() != null && !req.getName().isBlank()) {
            String newName = req.getName().trim().toUpperCase();
            Long projId = existing.getProject() != null ? existing.getProject().getId() : null;
            if (projId != null
                    && !existing.getName().equalsIgnoreCase(newName)
                    && categoryRepo.existsByNameIgnoreCaseAndProject_Id(newName, projId)) {
                return ResponseEntity.status(409).body("Category already exists in this project: " + newName);
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

        // set/clear owner link if provided explicitly
        if (aupId != null) {
            if (aupId <= 0) {
                existing.setOwnerProject(null);
            } else {
                aupRepo.findById(aupId).ifPresent(existing::setOwnerProject);
            }
        }

        return ResponseEntity.ok(categoryRepo.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!categoryRepo.existsById(id)) return ResponseEntity.notFound().build();
        categoryRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
