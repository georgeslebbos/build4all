package com.build4all.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.build4all.entities.BusinessUser;

public interface BusinessUserRepository extends JpaRepository<BusinessUser, Long> {
    List<BusinessUser> findByBusinessId(Long businessId);
}
