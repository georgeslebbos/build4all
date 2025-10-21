package com.build4all.app.service;

import com.build4all.app.domain.AppRequest;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppRequestService {

    private final AppRequestRepository appRequestRepo;
    private final AdminUserProjectRepository aupRepo;
    private final AdminUsersRepository adminRepo;
    private final ProjectRepository projectRepo;

    public AppRequestService(AppRequestRepository appRequestRepo,
                             AdminUserProjectRepository aupRepo,
                             AdminUsersRepository adminRepo,
                             ProjectRepository projectRepo) {
        this.appRequestRepo = appRequestRepo;
        this.aupRepo = aupRepo;
        this.adminRepo = adminRepo;
        this.projectRepo = projectRepo;
    }

    public AppRequest createRequest(Long ownerId, Long projectId, String appName, String notes) {
        AppRequest r = new AppRequest();
        r.setOwnerId(ownerId);
        r.setProjectId(projectId);
        r.setAppName(appName);
        r.setNotes(notes);
        return appRequestRepo.save(r);
    }

    @Transactional
    public AdminUserProject approve(Long requestId) {
        AppRequest req = appRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request already decided");
        }

        AdminUser owner = adminRepo.findById(req.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("Owner(admin) not found"));
        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        String slug = slugify(req.getAppName());

        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_Id(owner.getAdminId(), project.getId())
                .orElseGet(() -> new AdminUserProject(owner, project, null, null, null));

        link.setStatus("ACTIVE");
        link.setSlug(slug);
        aupRepo.save(link);

        req.setStatus("APPROVED");
        appRequestRepo.save(req);

        return link;
    }

    @Transactional
    public void reject(Long requestId) {
        AppRequest req = appRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request already decided");
        }
        req.setStatus("REJECTED");
        appRequestRepo.save(req);
    }

    @Transactional
    public AdminUserProject setApkUrl(Long adminId, Long projectId, String apkUrl) {
        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_Id(adminId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found"));
        link.setApkUrl(apkUrl);
        return aupRepo.save(link);
    }

    private String slugify(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
