package com.build4all.app.repository;

import com.build4all.app.domain.AppBuildJob;
import com.build4all.app.domain.BuildPlatform;
import com.build4all.app.domain.BuildJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppBuildJobRepository extends JpaRepository<AppBuildJob, Long> {

    Optional<AppBuildJob> findByCiBuildId(String ciBuildId);

    Optional<AppBuildJob> findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(Long appId, BuildPlatform platform);

    Optional<AppBuildJob> findTop1ByApp_IdAndPlatformAndStatusOrderByCreatedAtDesc(
            Long appId, BuildPlatform platform, BuildJobStatus status
    );

    List<AppBuildJob> findTop20ByApp_IdOrderByCreatedAtDesc(Long appId);
    
    Optional<AppBuildJob> findTop1ByApp_IdOrderByCreatedAtDesc(Long appId);

}
