package com.build4all.admin.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.AdminProjectAssignmentRequest;
import com.build4all.admin.dto.AdminProjectAssignmentResponse;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUserProjectService {

    private final AdminUsersRepository adminRepo;
    private final ProjectRepository projectRepo;
    private final AdminUserProjectRepository linkRepo;

    public AdminUserProjectService(AdminUsersRepository adminRepo,
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
        AdminUser admin = adminRepo.findByAdminId(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        Project project = projectRepo.findById(req.getProjectId())
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        AdminUserProject link = linkRepo
            .findByAdmin_AdminIdAndProject_Id(admin.getAdminId(), project.getId())
            .orElse(null);

        if (link == null) {
            link = new AdminUserProject(
                admin,
                project,
                req.getLicenseId(),
                req.getValidFrom(),
                req.getEndTo()
            );
            link.setStatus("ACTIVE");
        } else {
            link.setLicenseId(req.getLicenseId());
            link.setValidFrom(req.getValidFrom());
            link.setEndTo(req.getEndTo());
        }

        linkRepo.save(link);
    }

    @Transactional
    public void updateLicense(Long adminId, Long projectId, AdminProjectAssignmentRequest req) {
        AdminUserProject link = linkRepo
            .findByAdmin_AdminIdAndProject_Id(adminId, projectId)
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (req.getLicenseId() != null) link.setLicenseId(req.getLicenseId());
        if (req.getValidFrom() != null) link.setValidFrom(req.getValidFrom());
        if (req.getEndTo() != null) link.setEndTo(req.getEndTo());

        linkRepo.save(link);
    }

    @Transactional
    public void remove(Long adminId, Long projectId) {
        linkRepo.findByAdmin_AdminIdAndProject_Id(adminId, projectId)
            .ifPresent(linkRepo::delete);
    }
}
