package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUsers;
import com.build4all.admin.domain.AdminUserBusiness;
import com.build4all.business.domain.Businesses;
import com.build4all.admin.domain.AdminUserBusinessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminUserBusinessRepository extends JpaRepository<AdminUserBusiness, AdminUserBusinessId> {
    Optional<AdminUserBusiness> findByAdmin_AdminId(Long adminId);
    
    boolean existsByBusinessAndAdmin(Businesses business, AdminUsers admin);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdminUserBusiness ba WHERE ba.business.id = :businessId")
    void deleteByBusinessId(@Param("businessId") Long businessId);
}
