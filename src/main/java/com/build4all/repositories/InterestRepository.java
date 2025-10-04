package com.build4all.repositories;

import com.build4all.entities.Interest;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestRepository extends JpaRepository<Interest, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<Interest> findByNameIgnoreCase(String name);
}
