package com.build4all.catalog.repository;

import com.build4all.catalog.domain.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {

    // project/category scoping
    boolean existsByNameIgnoreCaseAndCategory_Id(String name, Long categoryId);
    Optional<ItemType> findByName(String name);
    List<ItemType> findAllByOrderByNameAsc();
    List<ItemType> findByCategory_IdOrderByNameAsc(Long categoryId);
    List<ItemType> findByCategory_Project_IdOrderByNameAsc(Long projectId);

    // owner filter (via aup_sid)
    List<ItemType> findByOwnerProject_IdOrderByNameAsc(Long aupId);

    // optional helpers
    List<ItemType> findByCategory_Id(Long categoryId);
    boolean existsByNameIgnoreCase(String name);
}
