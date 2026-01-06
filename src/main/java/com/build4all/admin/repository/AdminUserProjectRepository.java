package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.app.domain.AppRequest;
import com.build4all.project.dto.ProjectOwnerSummaryDTO;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for AdminUserProject (AUP).
 * An AUP record links an AdminUser to a Project and stores app/tenant metadata (slug, status, build URLs, etc.).
 *
 * Spring Data generates SQL automatically for method names like findByAdmin_AdminId(...).
 */
public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, Long> {

    /**
     * Fetch all AUP links for a given admin (owner/manager).
     * Equivalent to: SELECT * FROM admin_user_projects WHERE admin_id = ?
     */
    List<AdminUserProject> findByAdmin_AdminId(Long adminId);

    /**
     * Fetch all AUP links for a given project.
     * Equivalent to: SELECT * FROM admin_user_projects WHERE project_id = ?
     */
    List<AdminUserProject> findByProject_Id(Long projectId);

    /**
     * Checks if at least one link exists between this admin and this project.
     * Useful for validation/authorization without loading the full entity.
     */
    boolean existsByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);

    /**
     * Fetch link(s) between a specific admin and a specific project.
     * Returns List because there can be more than one link row (usually distinguished by slug).
     */
    List<AdminUserProject> findByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);

    /**
     * Fetch a single link by (adminId, projectId, slug).
     * Matches the unique constraint: admin_id + project_id + slug.
     */
    Optional<AdminUserProject> findByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);

    /**
     * Existence check for the (adminId, projectId, slug) combination.
     * Used to prevent duplicate slugs before insert/update.
     */
    boolean existsByAdmin_AdminIdAndProject_IdAndSlug(Long adminId, Long projectId, String slug);

    /**
     * Fetch a link by slug only.
     * Used when slug is used as a routing/tenant key.
     */
    Optional<AdminUserProject> findBySlug(String slug);

    /**
     * Returns a "slim" list of the owner's projects using projection OwnerProjectView.
     * Only selects the needed fields for listing (faster and lighter than loading full entities).
     */
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

    // ✅ Keep this
    /**
     * Fetch a link by id but only if it belongs to the given admin.
     * This is useful when an admin requests a record by ID while ensuring it matches their ownership.
     */
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


}
