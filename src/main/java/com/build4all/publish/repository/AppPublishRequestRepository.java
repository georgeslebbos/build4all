package com.build4all.publish.repository;



import com.build4all.publish.domain.*;
import com.build4all.publish.domain.AppPublishRequest;
import com.build4all.admin.domain.AdminUserProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppPublishRequestRepository extends JpaRepository<AppPublishRequest, Long> {

    Optional<AppPublishRequest> findFirstByAdminUserProjectAndPlatformAndStoreAndStatus(
            AdminUserProject adminUserProject,
            PublishPlatform platform,
            PublishStore store,
            PublishStatus status
    );

    List<AppPublishRequest> findByStatusOrderByRequestedAtDesc(PublishStatus status);

    List<AppPublishRequest> findByAdminUserProjectOrderByCreatedAtDesc(AdminUserProject adminUserProject);

    Optional<AppPublishRequest> findTopByAdminUserProjectAndPlatformAndStoreOrderByCreatedAtDesc(
            AdminUserProject adminUserProject,
            PublishPlatform platform,
            PublishStore store
    );
}
