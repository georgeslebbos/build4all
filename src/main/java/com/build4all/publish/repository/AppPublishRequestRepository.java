package com.build4all.publish.repository;



import com.build4all.publish.domain.*;
import com.build4all.publish.domain.AppPublishRequest;
import com.build4all.admin.domain.AdminUserProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    
    @EntityGraph(attributePaths = {"publisherProfile", "adminUserProject", "requestedBy", "reviewedBy"})
    @Query("select r from AppPublishRequest r where r.id = :id")
    Optional<AppPublishRequest> findByIdWithRefs(@Param("id") Long id);
    
    @Query("""
    	    select r from AppPublishRequest r
    	    join fetch r.adminUserProject a
    	    left join fetch r.publisherProfile p
    	    left join fetch r.requestedBy rb
    	    left join fetch r.reviewedBy rev
    	    where r.status = :status
    	    order by r.requestedAt desc
    	""")
    	List<AppPublishRequest> findByStatusForAdminWithJoins(@Param("status") PublishStatus status);
}

