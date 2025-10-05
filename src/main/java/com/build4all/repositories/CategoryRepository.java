package com.build4all.repositories;

import com.build4all.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {  
    boolean existsByNameIgnoreCaseAndProject_Id(String name, Long projectId);
    Optional<Category> findByNameIgnoreCaseAndProject_Id(String name, Long projectId);
    Optional<Category> findByNameIgnoreCase(String name);
    List<Category> findAllByOrderByNameAsc();
    List<Category> findByProject_IdOrderByNameAsc(Long projectId);
}
