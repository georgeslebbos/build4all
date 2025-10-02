package com.build4all.repositories;

import com.build4all.entities.BusinessStatus;
import com.build4all.entities.Businesses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessesRepository extends JpaRepository<Businesses, Long> {

    Businesses findByBusinessName(String businessName);

    // DO NOT override JpaRepository#findById(Long) with a different return type.
    // Remove any Businesses findById(long id) you might have locally.

    @Query("SELECT b FROM Businesses b WHERE b.email = :email AND b.email IS NOT NULL")
    Optional<Businesses> findByEmail(@Param("email") String email);

    @Query("SELECT b FROM Businesses b WHERE b.phoneNumber = :phoneNumber AND b.phoneNumber IS NOT NULL")
    Optional<Businesses> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    @Query("""
            SELECT b
            FROM Businesses b
            WHERE
              (b.email IS NOT NULL AND LOWER(b.email) = LOWER(:identifier))
              OR
              (b.phoneNumber IS NOT NULL AND b.phoneNumber = :identifier)
            """)
    Optional<Businesses> findByEmailOrPhone(@Param("identifier") String identifier);

    List<Businesses> findByIsPublicProfileTrueAndStatus(BusinessStatus status);

    boolean existsByBusinessNameIgnoreCase(String businessName);
    
    boolean existsByBusinessNameIgnoreCaseAndIdNot(String businessName, Long id);
}
