package com.build4all.repositories;

import com.build4all.entities.Interests;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestRepository extends JpaRepository<Interests, Long> {
    boolean existsByNameIgnoreCase(String name);
}
