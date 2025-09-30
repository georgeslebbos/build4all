package com.build4all.repositories;

import com.build4all.entities.FeedType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedTypeRepository extends JpaRepository<FeedType, Long> {
    Optional<FeedType> findByName(String name);
    boolean existsByName(String name);
}
