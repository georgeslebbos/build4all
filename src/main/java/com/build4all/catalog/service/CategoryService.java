package com.build4all.catalog.service;

import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) {
        this.repo = repo;
    }

    public List<Category> findAll() { return repo.findAll(); }
    public Optional<Category> findById(Long id) { return repo.findById(id); }
    public Category save(Category category) { return repo.save(category); }
    public void delete(Long id) { repo.deleteById(id); }

    public List<Category> listByProject(Long projectId) {
        return repo.findByProject_IdOrderByNameAsc(projectId);
    }
}
