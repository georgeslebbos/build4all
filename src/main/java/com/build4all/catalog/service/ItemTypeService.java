package com.build4all.catalog.service;

import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItemTypeService {

    private final ItemTypeRepository itemTypeRepository;
    private final CategoryRepository categoryRepository;

    public ItemTypeService(ItemTypeRepository itemTypeRepository,
                           CategoryRepository categoryRepository) {
        this.itemTypeRepository = itemTypeRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Return all ItemTypes (Category carries the Project).
     */
    @Transactional(readOnly = true)
    public List<ItemType> getAllItemTypes() {
        return itemTypeRepository.findAll();
    }

    /**
     * Create a new ItemType under a Category.
     * Project is implied via category.getProject().
     */
    @Transactional
    public ItemType createItemType(String name,
                                   String icon,
                                   String iconLib,
                                   Long categoryId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ItemType name is required");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId is required");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        ItemType type = new ItemType();
        type.setName(name.trim());
        type.setIcon(icon);
        type.setIconLibrary(iconLib);
        type.setCategory(category); // Project is accessible via category.getProject()

        return itemTypeRepository.save(type);
    }

    /**
     * Update an existing ItemType.
     */
    @Transactional
    public ItemType updateItemType(Long id,
                                   String name,
                                   String icon,
                                   String iconLib,
                                   Long categoryId) {
        ItemType existing = itemTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ItemType not found: " + id));

        if (name != null && !name.isBlank()) {
            existing.setName(name.trim());
        }
        if (icon != null) {
            existing.setIcon(icon);
        }
        if (iconLib != null) {
            existing.setIconLibrary(iconLib);
        }
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
            existing.setCategory(category); // project is implied by the category
        }

        return itemTypeRepository.save(existing);
    }

    /**
     * Delete an ItemType by id.
     */
    @Transactional
    public void deleteItemType(Long id) {
        itemTypeRepository.deleteById(id);
    }

    /*
     * Optional: if some callers still pass a projectId, you can validate it matches
     * the category’s project to avoid mismatches, e.g.:
     *
     * private void ensureCategoryBelongsToProject(Category category, Long expectedProjectId) {
     *     if (expectedProjectId == null) return;
     *     if (category.getProject() == null || !category.getProject().getId().equals(expectedProjectId)) {
     *         throw new IllegalArgumentException("Category does not belong to project id: " + expectedProjectId);
     *     }
     * }
     */
}
