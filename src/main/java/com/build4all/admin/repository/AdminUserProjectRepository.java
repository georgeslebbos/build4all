// src/main/java/com/build4all/admin/repository/AdminUserProjectRepository.java
package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.OwnerProjectView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, Long> {

    List<AdminUserProject> findByAdmin_AdminId(Long adminId);
    List<AdminUserProject> findByProject_Id(Long projectId);
    Optional<AdminUserProject> findByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);
    boolean existsByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);
    Optional<AdminUserProject> findBySlug(String slug);

    // Slim list used by the owner app (add appName + apkUrl is already there)
    @Query("""
        select 
            p.id as projectId,
            p.projectName as projectName,
            a.slug as slug,
            a.apkUrl as apkUrl
        from AdminUserProject a
        join a.project p
        where a.admin.adminId = :ownerId
        order by p.projectName
    """)
    List<OwnerProjectView> findOwnerProjectsSlim(@Param("ownerId") Long ownerId);
}
