package com.build4all.services;

import com.build4all.entities.Project;
import com.build4all.repositories.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository repo;

    public ProjectService(ProjectRepository repo) {
        this.repo = repo;
    }

    public List<Project> findAll() { return repo.findAll(); }

    public Project findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Transactional
    public Project create(String name, String description, Boolean active) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("projectName is required");
        if (repo.existsByProjectNameIgnoreCase(name))
            throw new IllegalArgumentException("Project name already exists");

        Project p = new Project();
        p.setProjectName(name.trim());
        p.setDescription(description);
        if (active != null) p.setActive(active);
        return repo.save(p);
    }

    @Transactional
    public Project update(Long id, String name, String description, Boolean active) {
        Project p = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (name != null && !name.isBlank()) {
            if (!p.getProjectName().equalsIgnoreCase(name) && repo.existsByProjectNameIgnoreCase(name))
                throw new IllegalArgumentException("Project name already exists");
            p.setProjectName(name.trim());
        }
        if (description != null) p.setDescription(description);
        if (active != null) p.setActive(active);
        return repo.save(p);
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
