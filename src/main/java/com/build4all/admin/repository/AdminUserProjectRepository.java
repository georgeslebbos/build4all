package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUserProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, Long> {

    List<AdminUserProject> findByAdmin_AdminId(Long adminId);

    List<AdminUserProject> findByProject_Id(Long projectId);

    Optional<AdminUserProject> findByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);

    boolean existsByAdmin_AdminIdAndProject_Id(Long adminId, Long projectId);

    Optional<AdminUserProject> findBySlug(String slug);
    
    
}
