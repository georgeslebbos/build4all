package com.build4all.services;

import com.build4all.entities.Category;
import com.build4all.repositories.CategoryRepository;
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
}
