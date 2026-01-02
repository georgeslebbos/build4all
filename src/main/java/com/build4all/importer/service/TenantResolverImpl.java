// File: src/main/java/com/build4all/feeders/importer/TenantResolverImpl.java
package com.build4all.importer.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.importer.dto.SeedDataset;
import com.build4all.project.domain.Project;
import com.build4all.project.domain.ProjectType;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class TenantResolverImpl implements TenantResolver {

    private final ProjectRepository projectRepo;
    private final RoleRepository roleRepo;
    private final AdminUsersRepository adminRepo;
    private final AdminUserProjectRepository aupRepo;
    private final PasswordEncoder passwordEncoder;

    public TenantResolverImpl(
            ProjectRepository projectRepo,
            RoleRepository roleRepo,
            AdminUsersRepository adminRepo,
            AdminUserProjectRepository aupRepo,
            PasswordEncoder passwordEncoder
    ) {
        this.projectRepo = projectRepo;
        this.roleRepo = roleRepo;
        this.adminRepo = adminRepo;
        this.aupRepo = aupRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Resolved resolveOrCreate(SeedDataset data) {
        // 1) Project
        Project project = projectRepo.findByProjectNameIgnoreCase(data.projectName)
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setProjectName(data.projectName);
                    p.setDescription(data.projectDescription);
                    p.setActive(true);

                    // If you added projectType to entity, set it:
                    if (data.projectType != null && !data.projectType.isBlank()) {
                        p.setProjectType(ProjectType.valueOf(data.projectType.trim().toUpperCase()));
                    } else {
                        // default
                        p.setProjectType(ProjectType.ECOMMERCE);
                    }

                    return projectRepo.save(p);
                });

        // 2) Role + owner admin
        String roleName = (data.owner != null && data.owner.role != null) ? data.owner.role : "OWNER";
        Role ownerRole = roleRepo.findByNameIgnoreCase(roleName)
                .orElseGet(() -> roleRepo.save(new Role(roleName.toUpperCase())));

        AdminUser owner = adminRepo.findByEmail(data.owner.email)
                .orElseGet(() -> {
                    AdminUser a = new AdminUser();
                    a.setUsername(data.owner.username);
                    a.setFirstName(data.owner.firstName);
                    a.setLastName(data.owner.lastName);
                    a.setEmail(data.owner.email);
                    a.setPasswordHash(passwordEncoder.encode(data.owner.password));
                    a.setRole(ownerRole);
                    return adminRepo.save(a);
                });

        // 3) Tenant link (AdminUserProject)
        String slug = data.tenant.slug;

        AdminUserProject aup = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(owner.getAdminId(), project.getId(), slug)
                .orElseGet(() -> {
                    AdminUserProject link = new AdminUserProject();
                    link.setAdmin(owner);
                    link.setProject(project);
                    link.setSlug(slug);
                    link.setAppName(data.tenant.appName);
                    link.setStatus(data.tenant.status != null ? data.tenant.status : "ACTIVE");
                    link.setValidFrom(LocalDate.now());
                    link.setEndTo(LocalDate.now().plusYears(1));
                    return aupRepo.save(link);
                });

        return new Resolved(project.getId(), aup.getId(), aup.getSlug());
    }
}
