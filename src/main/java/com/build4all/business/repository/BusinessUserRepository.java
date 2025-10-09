package com.build4all.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.build4all.business.domain.BusinessUser;

public interface BusinessUserRepository extends JpaRepository<BusinessUser, Long> {
    List<BusinessUser> findByBusinessId(Long businessId);
}
