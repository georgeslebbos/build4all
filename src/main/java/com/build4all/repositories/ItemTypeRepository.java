package com.build4all.repositories;

import java.util.Optional;
import java.util.List;

import com.build4all.entities.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {

    boolean existsByName(String type);

    // Correct query method - Spring Data will implement it for you
    List<ItemType> findAllByOrderByNameAsc();

    Optional<ItemType> findByName(String name);
    
 // NEW: help filter by project
    List<ItemType> findByProject_IdOrderByNameAsc(Long projectId);

    // Optional rule: if you want uniqueness per project (not global)
    boolean existsByNameIgnoreCaseAndProject_Id(String name, Long projectId);
    
    boolean existsByNameIgnoreCase(String name);
}
