package com.build4all.repositories;

import com.build4all.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    boolean existsByProjectNameIgnoreCase(String name);
    Optional<Project> findByProjectNameIgnoreCase(String name);
}
