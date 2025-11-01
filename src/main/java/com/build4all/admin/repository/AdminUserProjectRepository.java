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

    // 🔻 old single-app lookups are deprecated and removed to avoid misuse
 // in AdminUserProjectRepository.java
    boolean existsByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);
 // find a specific app row (owner+project+slug)
   

    // list ALL app rows for (owner+project)
    List<AdminUserProject> findByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);


    // ✅ app-aware lookups
    Optional<AdminUserProject> findByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);
    boolean existsByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);

    Optional<AdminUserProject> findBySlug(String slug); // keep if used elsewhere

    @Query("""
        select 
            p.id as projectId,
            p.projectName as projectName,
            a.slug as slug,
            a.appName as appName,
            a.apkUrl as apkUrl
        from AdminUserProject a
        join a.project p
        where a.admin.adminId = :ownerId
        order by p.projectName, a.slug
    """)
    List<OwnerProjectView> findOwnerProjectsSlim(@Param("ownerId") Long ownerId);
}
