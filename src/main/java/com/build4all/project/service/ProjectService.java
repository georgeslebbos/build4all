package com.build4all.project.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public Project create(String name, String description, Boolean active) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("projectName is required");
        if (repo.existsByProjectNameIgnoreCase(name))
            throw new IllegalArgumentException("Project name already exists");

        Project p = new Project();
        p.setProjectName(name.trim());
        p.setDescription(description);
        if (active != null) p.setActive(active);
        return repo.save(p);
    }

    @Transactional
    public Project update(Long id, String name, String description, Boolean active) {
        Project p = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (name != null && !name.isBlank()) {
            if (!p.getProjectName().equalsIgnoreCase(name) && repo.existsByProjectNameIgnoreCase(name))
                throw new IllegalArgumentException("Project name already exists");
            p.setProjectName(name.trim());
        }
        if (description != null) p.setDescription(description);
        if (active != null) p.setActive(active);
        return repo.save(p);
    }

    @Transactional
    public void delete(Long id) {
        // Clean owner links for this project, then delete the project
        var links = linkRepo.findByProject_Id(id);
        if (!links.isEmpty()) {
            linkRepo.deleteAll(links);
        }
        repo.deleteById(id);
    }

    // ---------- OWNER helpers ----------

    @Transactional
    public Project save(Project p) { return repo.save(p); }

    /** Link owner/admin (adminId) to project (projectId). Idempotent. */
    @Transactional
    public void linkProjectToOwner(Long adminId, Long projectId) {
        AdminUser owner = adminRepo.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found: " + adminId));
        Project project = repo.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        boolean exists = linkRepo.existsByAdmin_AdminIdAndProject_Id(adminId, projectId);
        if (!exists) {
            AdminUserProject link = new AdminUserProject(owner, project, null, null, null);
            link.setStatus("ACTIVE");
            linkRepo.save(link);
        }
    }

    /** Projects linked to a given AdminUser (owner/admin). */
    @Transactional(readOnly = true)
    public List<Project> findByOwnerAdminId(Long adminId) {
        return linkRepo.findByAdmin_AdminId(adminId)
                .stream()
                .map(AdminUserProject::getProject)
                .toList();
    }

    /** Guard: is this admin linked to the project? */
    @Transactional(readOnly = true)
    public boolean isOwnerLinkedToProject(Long adminId, Long projectId) {
        return linkRepo.existsByAdmin_AdminIdAndProject_Id(adminId, projectId);
    }
}
