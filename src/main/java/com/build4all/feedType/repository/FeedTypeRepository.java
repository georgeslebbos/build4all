package com.build4all.feedType.repository;

import com.build4all.feedType.FeedTypeSeeder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedTypeRepository extends JpaRepository<FeedTypeSeeder.FeedType, Long> {
    Optional<FeedTypeSeeder.FeedType> findByName(String name);
    boolean existsByName(String name);
}
