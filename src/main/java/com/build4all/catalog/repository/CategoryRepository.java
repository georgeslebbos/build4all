package com.build4all.catalog.repository;

import com.build4all.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // project-scoped
    boolean existsByNameIgnoreCaseAndProject_Id(String name, Long projectId);
    Optional<Category> findByNameIgnoreCaseAndProject_Id(String name, Long projectId);
    List<Category> findByProject_IdOrderByNameAsc(Long projectId);

    // owner-filtered (via aup_id)
    List<Category> findByOwnerProject_IdOrderByNameAsc(Long aupId);

    // generic
    Optional<Category> findByNameIgnoreCase(String name);
    List<Category> findAllByOrderByNameAsc();
}
