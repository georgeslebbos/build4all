package com.build4all.repositories;

import java.util.Optional;
import java.util.List;

import com.build4all.entities.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {

    boolean existsByName(String type);

    // Correct query method - Spring Data will implement it for you
    List<ItemType> findAllByOrderByNameAsc();

    Optional<ItemType> findByName(String name);
}
