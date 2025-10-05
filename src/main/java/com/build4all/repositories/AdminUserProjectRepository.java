package com.build4all.repositories;

import com.build4all.entities.AdminUserProject;
import com.build4all.entities.AdminUserProjectId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, AdminUserProjectId> {
    List<AdminUserProject> findByAdmin_AdminId(Long adminId);
    List<AdminUserProject> findByProject_Id(Long projectId);
}
