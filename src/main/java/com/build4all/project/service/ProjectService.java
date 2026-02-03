package com.build4all.project.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.domain.ProjectType;
import com.build4all.project.dto.ProjectOwnerSummaryDTO;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final ProjectRepository repo;
    private final AdminUsersRepository adminRepo;
    private final AdminUserProjectRepository linkRepo;

    public ProjectService(ProjectRepository repo,
                          AdminUsersRepository adminRepo,
                          AdminUserProjectRepository linkRepo) {
        this.repo = repo;
        this.adminRepo = adminRepo;
        this.linkRepo = linkRepo;
    }

    public List<Project> findAll() { return repo.findAll(); }

    public Project findById(Long id) { return repo.findById(id).orElse(null); }

    @Transactional
    public Project create(String name, String description, Boolean active, ProjectType projectType)
    {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("projectName is required");
        if (repo.existsByProjectNameIgnoreCase(name))
            throw new IllegalArgumentException("Project name already exists");

        Project p = new Project();
        p.setProjectName(name.trim());
        p.setDescription(description);
        if (active != null) p.setActive(active);
        p.setProjectType(projectType != null ? projectType : ProjectType.ECOMMERCE);
        return repo.save(p);
    }

    @Transactional
    public Project update(Long id, String name, String description, Boolean active, ProjectType projectType)
 {
        Project p = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (name != null && !name.isBlank()) {
            if (!p.getProjectName().equalsIgnoreCase(name) && repo.existsByProjectNameIgnoreCase(name))
                throw new IllegalArgumentException("Project name already exists");
            p.setProjectName(name.trim());
        }
        if (description != null) p.setDescription(description);
        if (active != null) p.setActive(active);
        
        if (projectType != null) p.setProjectType(projectType);
        return repo.save(p);
    }

    @Transactional
    public void delete(Long id) {
        // Delete ALL app rows belonging to this project, then delete the project
        var links = linkRepo.findByProject_Id(id);
        if (!links.isEmpty()) {
            linkRepo.deleteAll(links);
        }
        repo.deleteById(id);
    }

    // ---------- OWNER helpers ----------

    @Transactional
    public Project save(Project p) { return repo.save(p); }

    /**
     * Link owner/admin (adminId) to project (projectId).
     * In the new model, the existence of ANY app row under (owner, project) is considered the "link".
     *
     * Backward-compat behavior:
     * - If no app exists yet for (owner, project), create a minimal placeholder app row so
     *   old flows that relied on "link creation" keep working.
     * - If you prefer NO placeholder, simply return when "exists" is false (see comment below).
     */
    @Transactional
    public void linkProjectToOwner(Long adminId, Long projectId) {
        AdminUser owner = adminRepo.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found: " + adminId));
        Project project = repo.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        boolean exists = linkRepo.existsByAdmin_AdminIdAndProject_Id(adminId, projectId);
        if (exists) return;

        // ---- Option A (default): create a minimal placeholder app row to establish the link
        LocalDate now = LocalDate.now();
        String baseSlug = "app";
        String uniqueSlug = ensureUniqueSlug(adminId, projectId, baseSlug);

        AdminUserProject link = new AdminUserProject(owner, project, null, now, now.plusMonths(1));
        link.setStatus("TEST");
        link.setSlug(uniqueSlug);
        link.setAppName(project.getProjectName()); // display name; harmless
        link.setLicenseId("LIC-" + adminId + "-" + projectId + "-" + now + "-" + uniqueSlug);
        link.setApkUrl(null);
        linkRepo.save(link);

        // ---- Option B: if you DON'T want a placeholder, comment the block above and uncomment:
        // return;
    }

    /**
     * Projects linked to a given AdminUser (owner/admin).
     * Deduplicated (because multiple apps may exist under the same project).
     */
    @Transactional(readOnly = true)
    public List<Project> findByOwnerAdminId(Long adminId) {
        var rows = linkRepo.findByAdmin_AdminId(adminId);
        if (rows.isEmpty()) return List.of();

        // Deduplicate by projectId, preserving order
        Map<Long, Project> unique = new LinkedHashMap<>();
        for (AdminUserProject l : rows) {
            Project p = l.getProject();
            if (p != null) unique.putIfAbsent(p.getId(), p);
        }
        return List.copyOf(unique.values());
    }

    /** Guard: is this admin linked to the project? (i.e., any app row exists) */
    @Transactional(readOnly = true)
    public boolean isOwnerLinkedToProject(Long adminId, Long projectId) {
        return linkRepo.existsByAdmin_AdminIdAndProject_Id(adminId, projectId);
    }

    // ---------- helpers ----------

    /** Ensure slug uniqueness per (owner, project) by appending -2, -3, ... */
    private String ensureUniqueSlug(Long ownerId, Long projectId, String baseSlug) {
        if (baseSlug == null || baseSlug.isBlank()) baseSlug = "app";
        String candidate = baseSlug;
        int i = 2;
        while (linkRepo.existsByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, candidate)) {
            candidate = baseSlug + "-" + i;
            i++;
            if (i > 500) throw new IllegalStateException("Could not generate a unique slug");
        }
        return candidate;
    }
    
    @Transactional(readOnly = true)
    public List<ProjectOwnerSummaryDTO> ownersByProject(Long projectId) {
        return linkRepo.findOwnersByProject(projectId);
    }
    

    @Transactional(readOnly = true)
    public List<com.build4all.project.dto.OwnerAppInProjectDTO> appsByProjectAndOwner(Long projectId, Long adminId) {
        return linkRepo.findAppsByProjectAndOwner(projectId, adminId);
    }



}
