package com.build4all.services;

import com.build4all.entities.Interest;
import com.build4all.repositories.InterestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InterestService {

    private final InterestRepository repo;

    public InterestService(InterestRepository repo) {
        this.repo = repo;
    }

    public List<Interest> findAll() { return repo.findAll(); }
    public Optional<Interest> findById(Long id) { return repo.findById(id); }
    public Interest save(Interest interest) { return repo.save(interest); }
    public void delete(Long id) { repo.deleteById(id); }
}
