package com.build4all.business.repository;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessesRepository extends JpaRepository<Businesses, Long> {

    Optional<Businesses> findByBusinessName(String businessName);
    boolean existsByBusinessNameIgnoreCase(String businessName);
    boolean existsByBusinessNameIgnoreCaseAndIdNot(String businessName, Long id);

    Optional<Businesses> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<Businesses> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);

    // Handy combined finder (email OR phone)
    Optional<Businesses> findByEmailIgnoreCaseOrPhoneNumber(String email, String phoneNumber);

    List<Businesses> findByIsPublicProfileTrueAndStatus(BusinessStatus status);
}
