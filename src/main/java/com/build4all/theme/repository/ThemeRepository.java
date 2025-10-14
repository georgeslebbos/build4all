package com.build4all.theme.repository;

import com.build4all.theme.domain.Theme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ThemeRepository extends JpaRepository<Theme, Long> {
    boolean existsByName(String name);

    Optional<Theme> findById(Long id);

    Optional<Theme> findByIsActiveTrue();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Theme t SET t.isActive = false WHERE t.isActive = true")
    int deactivateAllThemes();

    @Modifying
    @Transactional
    @Query("UPDATE Theme t SET t.menuType = :menuType WHERE t.id = :id")
    void updateMenuTypeById(@Param("id") Long id, @Param("menuType") String menuType);

    @Query("SELECT t.menuType FROM Theme t WHERE t.isActive = true")
    Optional<String> findActiveMenuType();
}
