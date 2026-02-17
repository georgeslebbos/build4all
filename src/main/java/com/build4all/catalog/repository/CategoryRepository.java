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

    // generic
    Optional<Category> findByNameIgnoreCase(String name);
    List<Category> findAllByOrderByNameAsc();
    
    List<Category> findByOwnerProjectIdOrderByNameAsc(Long ownerProjectId);

    boolean existsByNameIgnoreCaseAndOwnerProjectId(String name, Long ownerProjectId);

    Optional<Category> findByNameIgnoreCaseAndOwnerProjectId(String name, Long ownerProjectId);
    
    Optional<Category> findByIdAndOwnerProjectId(Long id, Long ownerProjectId);
    
    boolean existsByNameIgnoreCaseAndOwnerProjectIdAndIdNot(String name, Long ownerProjectId, Long id);
	List<Category> findByProject_IdAndOwnerProjectIdOrderByNameAsc(Long projectId, Long ownerProjectId);


}
