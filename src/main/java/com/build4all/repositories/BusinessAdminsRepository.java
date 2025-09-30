package com.build4all.repositories;

import com.build4all.entities.BusinessAdmins;
import com.build4all.entities.*;
import com.build4all.entities.BusinessAdminsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusinessAdminsRepository extends JpaRepository<BusinessAdmins, BusinessAdminsId> {
    Optional<BusinessAdmins> findByAdmin_AdminId(Long adminId);
    
    boolean existsByBusinessAndAdmin(Businesses business, AdminUsers admin);

    @Modifying
    @Transactional
    @Query("DELETE FROM BusinessAdmins ba WHERE ba.business.id = :businessId")
    void deleteByBusinessId(@Param("businessId") Long businessId);
}
