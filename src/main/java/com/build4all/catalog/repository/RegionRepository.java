package com.build4all.catalog.repository;

import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {

    List<Region> findByCountryAndActiveTrue(Country country);

    Optional<Region> findByCountryAndCodeIgnoreCase(Country country, String code);
}
