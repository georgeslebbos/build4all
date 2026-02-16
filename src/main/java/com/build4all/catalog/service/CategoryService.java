package com.build4all.catalog.service;

import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) {
        this.repo = repo;
    }

    // ---------- BASIC ----------
    public List<Category> findAll() { return repo.findAll(); }

    public Optional<Category> findById(Long id) { return repo.findById(id); }

    public Category save(Category category) { return repo.save(category); }

    public void delete(Long id) { repo.deleteById(id); }

    // ---------- LISTING ----------
    @Transactional(readOnly = true)
    public List<Category> listByProject(Long projectId) {
        return repo.findByProject_IdOrderByNameAsc(projectId);
    }

    @Transactional(readOnly = true)
    public List<Category> listByOwnerProject(Long ownerProjectId) {
        return repo.findByOwnerProjectIdOrderByNameAsc(ownerProjectId);
    }

    // ---------- UNIQUENESS HELPERS ----------
    @Transactional(readOnly = true)
    public boolean existsByNameInOwnerProject(String name, Long ownerProjectId) {
        if (name == null || name.isBlank()) return false;
        return repo.existsByNameIgnoreCaseAndOwnerProjectId(name.trim(), ownerProjectId);
    }

    @Transactional(readOnly = true)
    public Optional<Category> findByNameInOwnerProject(String name, Long ownerProjectId) {
        if (name == null || name.isBlank()) return Optional.empty();
        return repo.findByNameIgnoreCaseAndOwnerProjectId(name.trim(), ownerProjectId);
    }
}
