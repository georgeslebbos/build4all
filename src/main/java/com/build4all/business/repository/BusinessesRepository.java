package com.build4all.business.repository;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessesRepository extends JpaRepository<Businesses, Long> {

    // ----- legacy (keep to avoid breaking old code) -----
    Optional<Businesses> findByBusinessName(String businessName);
    boolean existsByBusinessNameIgnoreCase(String businessName);
    boolean existsByBusinessNameIgnoreCaseAndIdNot(String businessName, Long id);

    Optional<Businesses> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<Businesses> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);

    Optional<Businesses> findByEmailIgnoreCaseOrPhoneNumber(String email, String phoneNumber);

    List<Businesses> findByIsPublicProfileTrueAndStatus(BusinessStatus status);

    // ----- tenant-aware (new) -----
    Optional<Businesses> findByOwnerProjectLink_IdAndEmail(Long ownerProjectLinkId, String email);
    Optional<Businesses> findByOwnerProjectLink_IdAndPhoneNumber(Long ownerProjectLinkId, String phone);

    boolean existsByOwnerProjectLink_IdAndEmail(Long ownerProjectLinkId, String email);
    boolean existsByOwnerProjectLink_IdAndPhoneNumber(Long ownerProjectLinkId, String phone);
    boolean existsByOwnerProjectLink_IdAndBusinessNameIgnoreCase(Long ownerProjectLinkId, String name);
    boolean existsByOwnerProjectLink_IdAndBusinessNameIgnoreCaseAndIdNot(Long ownerProjectLinkId, String name, Long id);

    List<Businesses> findByOwnerProjectLink_IdAndIsPublicProfileTrueAndStatus(Long ownerProjectLinkId, BusinessStatus status);
}
