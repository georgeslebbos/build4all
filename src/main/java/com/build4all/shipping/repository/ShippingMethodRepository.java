package com.build4all.shipping.repository;

import com.build4all.shipping.domain.ShippingMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShippingMethodRepository extends JpaRepository<ShippingMethod, Long> {

    // For OWNER/ADMIN listing
    List<ShippingMethod> findByOwnerProject_Id(Long ownerProjectId);

    // For public enabled methods
    List<ShippingMethod> findByOwnerProject_IdAndEnabledTrue(Long ownerProjectId);

    // secure lookup (prevents cross-tenant access by ID guessing)
    Optional<ShippingMethod> findByIdAndOwnerProject_Id(Long id, Long ownerProjectId);

    // secure existence check
    boolean existsByIdAndOwnerProject_Id(Long id, Long ownerProjectId);
}
