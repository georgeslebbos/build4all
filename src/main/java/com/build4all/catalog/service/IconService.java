package com.build4all.catalog.service;

import com.build4all.catalog.domain.Icon;
import com.build4all.catalog.repository.IconRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IconService {

    private final IconRepository repo;

    public IconService(IconRepository repo) {
        this.repo = repo;
    }

    public List<Icon> findAll() { return repo.findAll(); }
    public Optional<Icon> findById(Long id) { return repo.findById(id); }
    public Icon save(Icon icon) { return repo.save(icon); }
    public void delete(Long id) { repo.deleteById(id); }
}
