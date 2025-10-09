package com.build4all.catalog.repository;

import java.util.Optional;
import java.util.List;

import com.build4all.catalog.domain.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {

    boolean existsByNameIgnoreCaseAndCategory_Id(String name, Long categoryId);
    Optional<ItemType> findByName(String name);
    List<ItemType> findAllByOrderByNameAsc();

    // filter by project through the category
    List<ItemType> findByCategory_IdOrderByNameAsc(Long categoryId);

    List<ItemType> findByCategory_Project_IdOrderByNameAsc(Long projectId);

    // optional helpers
    List<ItemType> findByCategory_Id(Long categoryId);
    boolean existsByNameIgnoreCase(String name); // if you still use it elsewhere
}
