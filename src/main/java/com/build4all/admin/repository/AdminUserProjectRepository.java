package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.domain.AdminUserProjectId;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface AdminUserProjectRepository extends JpaRepository<AdminUserProject, AdminUserProjectId> {

    List<AdminUserProject> findByAdmin_AdminId(Long adminId);
    List<AdminUserProject> findByProject_Id(Long projectId);

    Optional<AdminUserProject> findById_AdminIdAndId_ProjectId(Long adminId, Long projectId);
    boolean existsById_AdminIdAndId_ProjectId(Long adminId, Long projectId);

    @Modifying
    @Transactional
    @Query("""
    DELETE FROM AdminUserProject aup
    WHERE aup.id.adminId = :adminId AND aup.id.projectId = :projectId
  """)
    void deleteById_AdminIdAndId_ProjectId(@Param("adminId") Long adminId, @Param("projectId") Long projectId);
}
