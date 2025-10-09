package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.domain.AdminUserProjectId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, AdminUserProjectId> {
    List<AdminUserProject> findByAdmin_AdminId(Long adminId);
    List<AdminUserProject> findByProject_Id(Long projectId);
}
