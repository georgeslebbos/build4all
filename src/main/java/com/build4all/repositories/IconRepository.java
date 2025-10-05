package com.build4all.repositories;

import com.build4all.entities.Icon;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IconRepository extends JpaRepository<Icon, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<Icon> findByNameIgnoreCase(String name);
}
