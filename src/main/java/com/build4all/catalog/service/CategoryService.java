// src/main/java/com/build4all/catalog/service/CategoryService.java
package com.build4all.catalog.service;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.Icon;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.dto.CategoryRequest;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.IconRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepo;
    private final IconRepository iconRepo;
    private final ProjectRepository projectRepo;
    private final AdminUserProjectRepository aupRepo;

    private final ItemTypeRepository itemTypeRepo;
    private final ItemRepository itemRepo;

    public CategoryService(CategoryRepository categoryRepo,
                           IconRepository iconRepo,
                           ProjectRepository projectRepo,
                           AdminUserProjectRepository aupRepo,
                           ItemTypeRepository itemTypeRepo,
                           ItemRepository itemRepo) {
        this.categoryRepo = categoryRepo;
        this.iconRepo = iconRepo;
        this.projectRepo = projectRepo;
        this.aupRepo = aupRepo;
        this.itemTypeRepo = itemTypeRepo;
        this.itemRepo = itemRepo;
    }

    // ✅ Result object
    public record DeleteCascadeResult(long deletedItemTypes) {}

    // ---------------- Helpers ----------------

    private String normalizeName(String name) {
        return name == null ? null : name.trim().toUpperCase();
    }

    private String normalizeIconLib(String iconLib) {
        return (iconLib == null || iconLib.isBlank()) ? "Ionicons" : iconLib.trim();
    }

    private Long resolveProjectIdFromOwnerProject(Long ownerProjectId) {
        return aupRepo.findProjectIdByLinkId(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ownerProject not found: " + ownerProjectId));
    }

    private void ensureIconExistsIfNeeded(boolean ensureIconExists, String iconName, String iconLib) {
        if (!ensureIconExists) return;
        if (iconName == null || iconName.isBlank()) return;

        String nm = iconName.trim();
        String lib = normalizeIconLib(iconLib);

        Optional<Icon> icOpt = iconRepo.findByNameIgnoreCase(nm);
        if (icOpt.isEmpty()) {
            iconRepo.save(new Icon(nm, lib));
            return;
        }

        Icon ic = icOpt.get();
        if (ic.getLibrary() == null || !ic.getLibrary().equalsIgnoreCase(lib)) {
            ic.setLibrary(lib);
            iconRepo.save(ic);
        }
    }

    private Project resolveProjectRefForTenant(Long ownerProjectId) {
        Long projectId = resolveProjectIdFromOwnerProject(ownerProjectId);
        return projectRepo.getReferenceById(projectId);
    }

    // ---------------- READ ----------------

    @Transactional(readOnly = true)
    public List<Category> listAllForOwnerProject(Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        return categoryRepo.findByOwnerProjectIdOrderByNameAsc(ownerProjectId);
    }

    @Transactional(readOnly = true)
    public List<Category> listByProjectForTenant(Long projectId, Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (projectId == null) throw new IllegalArgumentException("projectId is required");

        // ✅ Hard check: project must match tenant's project (AUP link)
        Long tenantProjectId = resolveProjectIdFromOwnerProject(ownerProjectId);
        if (!tenantProjectId.equals(projectId)) {
            // silent: don't leak if project exists in other tenant
            throw new IllegalArgumentException("Category not found");
        }

        return categoryRepo.findByProject_IdAndOwnerProjectIdOrderByNameAsc(projectId, ownerProjectId);
    }

    @Transactional(readOnly = true)
    public Category getForTenant(Long id, Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");

        return categoryRepo.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
    }

    // ---------------- CREATE ----------------

    @Transactional
    public Category createForTenant(Long ownerProjectId, CategoryRequest req, boolean ensureIconExists) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.getName() == null || req.getName().isBlank()) throw new IllegalArgumentException("name is required");

        String name = normalizeName(req.getName());
        String iconName = (req.getIconName() == null || req.getIconName().isBlank())
                ? null : req.getIconName().trim();
        String iconLib = normalizeIconLib(req.getIconLibrary());

        if (categoryRepo.existsByNameIgnoreCaseAndOwnerProjectId(name, ownerProjectId)) {
            throw new IllegalArgumentException("Category already exists in this owner project: " + name);
        }

        ensureIconExistsIfNeeded(ensureIconExists, iconName, iconLib);

        Project projectRef = resolveProjectRefForTenant(ownerProjectId);

        Category entity = new Category();
        entity.setProject(projectRef);
        entity.setOwnerProjectId(ownerProjectId);
        entity.setName(name);
        entity.setIconName(iconName);
        entity.setIconLibrary(iconLib);

        return categoryRepo.save(entity);
    }

    // ---------------- UPDATE ----------------

    @Transactional
    public Category updateForTenant(Long id, Long ownerProjectId, CategoryRequest req, boolean ensureIconExists) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (req == null) throw new IllegalArgumentException("body is required");

        Category existing = categoryRepo.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        // name
        if (req.getName() != null && !req.getName().isBlank()) {
            String newName = normalizeName(req.getName());

            boolean changed = existing.getName() == null || !existing.getName().equalsIgnoreCase(newName);
            if (changed) {
                var dup = categoryRepo.findByNameIgnoreCaseAndOwnerProjectId(newName, ownerProjectId);
                if (dup.isPresent() && !dup.get().getId().equals(existing.getId())) {
                    throw new IllegalArgumentException("Category already exists in this owner project: " + newName);
                }
            }
            existing.setName(newName);
        }

        // icon name
        if (req.getIconName() != null) {
            String newIcon = req.getIconName().trim();
            if (newIcon.isBlank()) {
                existing.setIconName(null);
            } else {
                existing.setIconName(newIcon);
                if (ensureIconExists) {
                    String lib = normalizeIconLib(req.getIconLibrary() != null ? req.getIconLibrary() : existing.getIconLibrary());
                    ensureIconExistsIfNeeded(true, newIcon, lib);
                }
            }
        }

        // icon library
        if (req.getIconLibrary() != null && !req.getIconLibrary().isBlank()) {
            String newLib = req.getIconLibrary().trim();
            existing.setIconLibrary(newLib);

            if (ensureIconExists && existing.getIconName() != null && !existing.getIconName().isBlank()) {
                ensureIconExistsIfNeeded(true, existing.getIconName(), newLib);
            }
        }

        return categoryRepo.save(existing);
    }

    // ---------------- DELETE (your cascade logic, tenant-safe) ----------------

    @Transactional
    public DeleteCascadeResult deleteCategoryCascade(Long categoryId, Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");

        Category cat = categoryRepo.findByIdAndOwnerProjectId(categoryId, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        // ✅ block if items use this category
        long itemsCount = itemRepo.countByCategoryId(categoryId); // (optional: make tenant-scoped in repo)
        if (itemsCount > 0) {
            throw new DeleteBlockedException("CATEGORY_DELETE_HAS_ITEMS", itemsCount);
        }

        try {
            // ✅ tenant-scoped item types lookup
            List<ItemType> types =
                    itemTypeRepo.findByCategory_IdAndCategory_OwnerProjectIdOrderByNameAsc(categoryId, ownerProjectId);

            long deletedTypes = types.size();

            if (!types.isEmpty()) {
                itemTypeRepo.deleteAll(types);
                itemTypeRepo.flush();
            }

            categoryRepo.delete(cat);
            categoryRepo.flush();

            return new DeleteCascadeResult(deletedTypes);

        } catch (DataIntegrityViolationException e) {
            throw new DeleteBlockedException("CATEGORY_DELETE_CONFLICT", 0);
        }
    }
}
