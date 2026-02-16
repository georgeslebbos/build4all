package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.app.dto.SuperAdminAppDetailsDto;
import com.build4all.app.dto.SuperAdminAppRowDto;
import com.build4all.project.dto.ProjectOwnerSummaryDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for AdminUserProject (AUP).
 * An AUP record links an AdminUser to a Project and stores app/tenant metadata (slug, status, build URLs, etc.).
 */
public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, Long> {

    List<AdminUserProject> findByAdmin_AdminId(Long adminId);

    List<AdminUserProject> findByProject_Id(Long projectId);

    boolean existsByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);

    List<AdminUserProject> findByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);

    Optional<AdminUserProject> findByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);

    boolean existsByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);

    Optional<AdminUserProject> findBySlug(String slug);
    
    @Query("""
    		  select a.project.id
    		  from AdminUserProject a
    		  where a.id = :linkId
    		""")
    		Optional<Long> findProjectIdByLinkId(@Param("linkId") Long linkId);

    
    @Query("""
    		  select
    		    l.id                 as linkId,
    		    p.id                 as projectId,
    		    p.projectName        as projectName,
    		    l.slug               as slug,
    		    l.appName            as appName,
    		    l.status             as status,
    		    l.apkUrl             as apkUrl,
    		    l.ipaUrl             as ipaUrl,
    		    l.bundleUrl          as bundleUrl,
    		    l.logoUrl            as logoUrl,
    		    l.androidPackageName as androidPackageName,
    		    l.iosBundleId        as iosBundleId,

    		    
    		    (
    		      select concat('', j.status)
    		      from AppBuildJob j
    		      where j.app.id = l.id
    		        and j.platform = com.build4all.app.domain.BuildPlatform.ANDROID
    		        and j.id = (
    		          select max(j2.id)
    		          from AppBuildJob j2
    		          where j2.app.id = l.id
    		            and j2.platform = com.build4all.app.domain.BuildPlatform.ANDROID
    		        )
    		    ) as androidBuildStatus,

    		  
    		    (
    		      select j.error
    		      from AppBuildJob j
    		      where j.app.id = l.id
    		        and j.platform = com.build4all.app.domain.BuildPlatform.ANDROID
    		        and j.id = (
    		          select max(j2.id)
    		          from AppBuildJob j2
    		          where j2.app.id = l.id
    		            and j2.platform = com.build4all.app.domain.BuildPlatform.ANDROID
    		        )
    		    ) as androidBuildError,

    		 
    		    (
    		      select concat('', j.status)
    		      from AppBuildJob j
    		      where j.app.id = l.id
    		        and j.platform = com.build4all.app.domain.BuildPlatform.IOS
    		        and j.id = (
    		          select max(j2.id)
    		          from AppBuildJob j2
    		          where j2.app.id = l.id
    		            and j2.platform = com.build4all.app.domain.BuildPlatform.IOS
    		        )
    		    ) as iosBuildStatus,

    		 
    		    (
    		      select j.error
    		      from AppBuildJob j
    		      where j.app.id = l.id
    		        and j.platform = com.build4all.app.domain.BuildPlatform.IOS
    		        and j.id = (
    		          select max(j2.id)
    		          from AppBuildJob j2
    		          where j2.app.id = l.id
    		            and j2.platform = com.build4all.app.domain.BuildPlatform.IOS
    		        )
    		    ) as iosBuildError

    		  from AdminUserProject l
    		  join l.project p
    		  where l.admin.adminId = :ownerId
    		  order by l.createdAt desc
    		""")
    		List<OwnerProjectView> findOwnerProjectsSlim(@Param("ownerId") Long ownerId);


    Optional<AdminUserProject> findByIdAndAdmin_AdminId(Long id, Long ownerId);

    @Query("""
        select new com.build4all.project.dto.ProjectOwnerSummaryDTO(
            a.admin.adminId,
            concat(a.admin.firstName, ' ', a.admin.lastName),
            a.admin.email,
            count(a.id)
        )
        from AdminUserProject a
        where a.project.id = :projectId
        group by a.admin.adminId, a.admin.firstName, a.admin.lastName, a.admin.email
        order by count(a.id) desc
    """)
    List<ProjectOwnerSummaryDTO> findOwnersByProject(@Param("projectId") Long projectId);

    List<AdminUserProject> findByProject_IdAndAdmin_AdminId(Long projectId, Long adminId);

    @Query("""
          select new com.build4all.project.dto.OwnerAppInProjectDTO(
            a.id,
            a.slug,
            a.appName,
            a.status,
            a.apkUrl,
            a.ipaUrl,
            a.bundleUrl
          )
          from AdminUserProject a
          where a.project.id = :projectId
            and a.admin.adminId = :adminId
          order by a.createdAt desc
        """)
    List<com.build4all.project.dto.OwnerAppInProjectDTO> findAppsByProjectAndOwner(
            @Param("projectId") Long projectId,
            @Param("adminId") Long adminId
    );

    @Query("""
          select a.admin.aiEnabled
          from AdminUserProject a
          where a.id = :linkId
        """)
    Optional<Boolean> isOwnerAiEnabledByLinkId(@Param("linkId") Long linkId);

    @Query("""
          select a.admin.adminId
          from AdminUserProject a
          where a.id = :linkId
        """)
    Optional<Long> findOwnerIdByLinkId(@Param("linkId") Long linkId);

    @Query("""
          select concat(a.admin.firstName, ' ', a.admin.lastName)
          from AdminUserProject a
          where a.id = :linkId
        """)
    Optional<String> findOwnerNameByLinkId(@Param("linkId") Long linkId);

    // =====================================================================
    // ✅ SUPER ADMIN SAFE QUERIES (DTOs) — FIXES LazyInitializationException
    // =====================================================================

    

    @Query("""
        select new com.build4all.app.dto.SuperAdminAppRowDto(
            a.id,
            a.admin.adminId,
            a.admin.username,
            a.project.id,
            a.project.projectName,
            a.slug,
            a.appName,
            a.status,
            a.androidPackageName,
            a.androidVersionName,
            a.androidVersionCode,
            a.apkUrl,
            a.bundleUrl,
            a.iosBundleId,
            a.iosVersionName,
            a.iosBuildNumber,
            a.ipaUrl,
            a.validFrom,
            a.endTo
        )
        from AdminUserProject a
        order by a.id desc
    """)
    List<SuperAdminAppRowDto> findAllForSuperAdmin();

    

    @Query("""
        select new com.build4all.app.dto.SuperAdminAppDetailsDto(
            a.id,

            a.admin.adminId,
            a.admin.username,

            a.project.id,
            a.project.projectName,

            a.slug,
            a.appName,
            a.status,

            a.licenseId,
            a.themeId,
            a.logoUrl,

            a.validFrom,
            a.endTo,

            case when a.currency is null then null else a.currency.id end,

            a.androidPackageName,
            a.androidVersionName,
            a.androidVersionCode,
            a.apkUrl,
            a.bundleUrl,

            a.iosBundleId,
            a.iosVersionName,
            a.iosBuildNumber,
            a.ipaUrl
        )
        from AdminUserProject a
        where a.id = :linkId
    """)
    Optional<SuperAdminAppDetailsDto> findDetailsForSuperAdmin(@Param("linkId") Long linkId);
    
    
 // ✅ SUPER ADMIN: fetch owner username/email WITHOUT loading AdminUser entity
    @Query("""
      select a.admin.username
      from AdminUserProject a
      where a.id = :linkId
    """)
    Optional<String> findOwnerUsernameByLinkId(@Param("linkId") Long linkId);

  



}
