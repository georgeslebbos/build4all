// src/main/java/com/build4all/catalog/service/ItemTypeService.java
package com.build4all.catalog.service;

import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ItemTypeService {

    private final ItemTypeRepository itemTypeRepository;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    public ItemTypeService(ItemTypeRepository itemTypeRepository,
                           CategoryRepository categoryRepository,
                           ItemRepository itemRepository) {
        this.itemTypeRepository = itemTypeRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemType> listAllForOwnerProject(Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId claim is missing");
        return itemTypeRepository.findByCategory_OwnerProjectIdOrderByNameAsc(ownerProjectId);
    }

    @Transactional
    public ItemType createItemType(String name,
                                   String icon,
                                   String iconLib,
                                   Long categoryId,
                                   Long ownerProjectId) {

        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId claim is missing");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (categoryId == null) throw new IllegalArgumentException("categoryId is required");

        Category category = categoryRepository.findByIdAndOwnerProjectId(categoryId, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        ItemType type = new ItemType();
        type.setName(name.trim());
        type.setIcon(icon);
        type.setIconLibrary(iconLib);
        type.setCategory(category);

        return itemTypeRepository.save(type);
    }

    @Transactional
    public ItemType updateItemType(Long id,
                                   String name,
                                   String icon,
                                   String iconLib,
                                   Long categoryId,
                                   Long ownerProjectId) {

        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId claim is missing");

        ItemType existing = itemTypeRepository.findByIdAndCategory_OwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ItemType not found: " + id));

        if (name != null && !name.isBlank()) existing.setName(name.trim());
        if (icon != null) existing.setIcon(icon);
        if (iconLib != null) existing.setIconLibrary(iconLib);

        if (categoryId != null) {
            Category newCat = categoryRepository.findByIdAndOwnerProjectId(categoryId, ownerProjectId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
            existing.setCategory(newCat);
        }

        return itemTypeRepository.save(existing);
    }

    @Transactional
    public void deleteItemType(Long id, Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId claim is missing");

        ItemType existing = itemTypeRepository.findByIdAndCategory_OwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ItemType not found: " + id));

        // 1) Block if used
        long usedCount = itemRepository.countByItemType_Id(id);
        if (usedCount > 0) {
            throw new DeleteBlockedException("ITEMTYPE_DELETE_HAS_ITEMS", usedCount);
        }

        Long categoryId = existing.getCategory().getId();

        try {
            // 2) default replacement
            if (existing.isDefaultForCategory()) {
                ItemType replacement = itemTypeRepository
                        .findFirstByCategory_IdAndIdNotAndCategory_OwnerProjectIdOrderByNameAsc(categoryId, id, ownerProjectId)
                        .orElseThrow(() -> new IllegalStateException("ITEMTYPE_DELETE_DEFAULT_ONLY_ONE"));

                replacement.setDefaultForCategory(true);
                itemTypeRepository.save(replacement);
                itemTypeRepository.flush();
            }

            // 3) delete
            itemTypeRepository.delete(existing);
            itemTypeRepository.flush();

        } catch (DataIntegrityViolationException e) {
            throw new DeleteBlockedException("ITEMTYPE_DELETE_CONFLICT", 0);
        }
    }

    @Transactional(readOnly = true)
    public List<ItemType> listByProject(Long projectId, Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId claim is missing");
        return itemTypeRepository.findByCategory_Project_IdAndCategory_OwnerProjectIdOrderByNameAsc(projectId, ownerProjectId);
    }

    @Transactional(readOnly = true)
    public List<ItemType> listByCategory(Long categoryId, Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId claim is missing");
        return itemTypeRepository.findByCategory_IdAndCategory_OwnerProjectIdOrderByNameAsc(categoryId, ownerProjectId);
    }
}
