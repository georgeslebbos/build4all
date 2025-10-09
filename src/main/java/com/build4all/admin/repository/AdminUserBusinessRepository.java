package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserBusiness;
import com.build4all.business.domain.Businesses;
import com.build4all.admin.domain.AdminUserBusinessId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminUserBusinessRepository extends JpaRepository<AdminUserBusiness, AdminUserBusinessId> {

    List<AdminUserBusiness> findByAdmin_AdminId(Long adminId);
    List<AdminUserBusiness> findByBusiness_Id(Long businessId);

    boolean existsByAdmin_AdminIdAndBusiness_Id(Long adminId, Long businessId);

    @Modifying
    @Transactional
    @Query("""
    DELETE FROM AdminUserBusiness aub
    WHERE aub.admin.adminId = :adminId AND aub.business.id = :businessId
  """)
    void deleteByAdmin_AdminIdAndBusiness_Id(@Param("adminId") Long adminId, @Param("businessId") Long businessId);

    @EntityGraph(attributePaths = {"admin","business"})
    List<AdminUserBusiness> findAllByAdmin_AdminId(Long adminId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdminUserBusiness ba WHERE ba.business.id = :businessId")
    void deleteByBusinessId(@Param("businessId") Long businessId);
}