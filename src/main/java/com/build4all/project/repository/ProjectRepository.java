package com.build4all.project.repository;

import com.build4all.project.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByProjectNameIgnoreCase(String projectName);
    boolean existsByProjectNameIgnoreCase(String projectName);
    Optional<Project> findById(Long id);
    boolean existsById(Long id);
}
