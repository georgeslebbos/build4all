// com/build4all/catalog/repository/ItemTypeRepository.java
package com.build4all.catalog.repository;

import com.build4all.catalog.domain.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {

    // Used by the seeder to avoid duplicates
    Optional<ItemType> findByName(String name);

    // Project-scoped via the item's category → project
    List<ItemType> findByCategory_Project_IdOrderByNameAsc(Long projectId);

    // Direct by category
    List<ItemType> findByCategory_IdOrderByNameAsc(Long categoryId);

    // 🔴 NEW: find the "default" ItemType for a given category
    Optional<ItemType> findByCategory_IdAndDefaultForCategoryTrue(Long categoryId);
    
    Optional<ItemType> findByNameIgnoreCaseAndCategory_Project_Id(String name, Long projectId);
    

    boolean existsByCategory_Id(Long categoryId);

    long deleteByCategory_Id(Long categoryId);
    

 

    // ✅ helpful for reassignment
    Optional<ItemType> findFirstByCategory_IdAndIdNotOrderByNameAsc(Long categoryId, Long excludedId);
    
    
    List<ItemType> findByCategory_OwnerProjectIdOrderByNameAsc(Long ownerProjectId);

    List<ItemType> findByCategory_Project_IdAndCategory_OwnerProjectIdOrderByNameAsc(Long projectId, Long ownerProjectId);

    List<ItemType> findByCategory_IdAndCategory_OwnerProjectIdOrderByNameAsc(Long categoryId, Long ownerProjectId);

    Optional<ItemType> findByIdAndCategory_OwnerProjectId(Long id, Long ownerProjectId);

    Optional<ItemType> findFirstByCategory_IdAndIdNotAndCategory_OwnerProjectIdOrderByNameAsc(
            Long categoryId, Long id, Long ownerProjectId
    );

}
