package com.build4all.app.repository;

import com.build4all.app.domain.AppRuntimeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRuntimeConfigRepository extends JpaRepository<AppRuntimeConfig, Long> {
    Optional<AppRuntimeConfig> findByApp_Id(Long aupId);
    Optional<AppRuntimeConfig> findByApp_Admin_AdminIdAndApp_Project_IdAndApp_Slug(Long ownerId, Long projectId, String slug);
}
