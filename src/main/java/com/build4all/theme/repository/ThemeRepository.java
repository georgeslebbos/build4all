// src/main/java/com/build4all/theme/repository/ThemeRepository.java
package com.build4all.theme.repository;

import com.build4all.theme.domain.Theme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ThemeRepository extends JpaRepository<Theme, Long> {

    boolean existsByName(String name);

    Optional<Theme> findByIsActiveTrue();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Theme t SET t.isActive = false WHERE t.isActive = true")
    int deactivateAllThemes();
}
