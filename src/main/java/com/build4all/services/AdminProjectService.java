package com.build4all.services;

import com.build4all.dto.AdminProjectAssignmentRequest;
import com.build4all.dto.AdminProjectAssignmentResponse;
import com.build4all.entities.*;
import com.build4all.repositories.AdminUserProjectRepository;
import com.build4all.repositories.AdminUsersRepository;
import com.build4all.repositories.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminProjectService {

    private final AdminUsersRepository adminRepo;
    private final ProjectRepository projectRepo;
    private final AdminUserProjectRepository linkRepo;

    public AdminProjectService(AdminUsersRepository adminRepo,
                               ProjectRepository projectRepo,
                               AdminUserProjectRepository linkRepo) {
        this.adminRepo = adminRepo;
        this.projectRepo = projectRepo;
        this.linkRepo = linkRepo;
    }

    @Transactional(readOnly = true)
    public List<AdminProjectAssignmentResponse> list(Long adminId) {
        return linkRepo.findByAdmin_AdminId(adminId).stream()
                .map(l -> new AdminProjectAssignmentResponse(
                        l.getProject().getId(),
                        l.getProject().getProjectName(),
                        l.getLicenseId(),
                        l.getValidFrom(),
                        l.getEndTo()
                ))
                .toList();
    }

    @Transactional
    public void assign(Long adminId, AdminProjectAssignmentRequest req) {
        AdminUsers admin = adminRepo.findByAdminId(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        AdminUserProjectId id = new AdminUserProjectId(admin.getAdminId(), project.getId());
        AdminUserProject link = linkRepo.findById(id).orElse(null);
        if (link == null) {
            link = new AdminUserProject(admin, project, req.getLicenseId(), req.getValidFrom(), req.getEndTo());
        } else {
            // upsert behavior
            link.setLicenseId(req.getLicenseId());
            link.setValidFrom(req.getValidFrom());
            link.setEndTo(req.getEndTo());
        }
        linkRepo.save(link);
    }

    @Transactional
    public void updateLicense(Long adminId, Long projectId, AdminProjectAssignmentRequest req) {
        AdminUserProjectId id = new AdminUserProjectId(adminId, projectId);
        AdminUserProject link = linkRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (req.getLicenseId() != null) link.setLicenseId(req.getLicenseId());
        if (req.getValidFrom() != null) link.setValidFrom(req.getValidFrom());
        if (req.getEndTo() != null) link.setEndTo(req.getEndTo());

        linkRepo.save(link);
    }

    @Transactional
    public void remove(Long adminId, Long projectId) {
        AdminUserProjectId id = new AdminUserProjectId(adminId, projectId);
        linkRepo.deleteById(id);
    }
}
