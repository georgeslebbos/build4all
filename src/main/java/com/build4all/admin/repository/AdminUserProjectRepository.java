package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.app.domain.AppRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, Long> {

    List<AdminUserProject> findByAdmin_AdminId(Long adminId);
    List<AdminUserProject> findByProject_Id(Long projectId);

    boolean existsByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);
    List<AdminUserProject> findByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);

    Optional<AdminUserProject> findByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);
    boolean existsByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);

    Optional<AdminUserProject> findBySlug(String slug);

    @Query("""
      select 
        l.id           as linkId,
        p.id           as projectId,
        p.projectName  as projectName,
        l.slug         as slug,
        l.appName      as appName,
        l.status       as status,
        l.apkUrl       as apkUrl,
        l.ipaUrl       as ipaUrl,
        l.bundleUrl    as bundleUrl
      from AdminUserProject l
      join l.project p
      where l.admin.adminId = :ownerId
      order by l.createdAt desc
    """)
    List<OwnerProjectView> findOwnerProjectsSlim(@Param("ownerId") Long ownerId);

    // ❌ REMOVE this (it causes your crash)
    // Optional<AdminUserProject> findByIdAndOwnerId(Long id, Long ownerId);

    // ✅ Keep this
    Optional<AdminUserProject> findByIdAndAdmin_AdminId(Long id, Long ownerId);
}
