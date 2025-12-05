package com.build4all.catalog.repository;

import com.build4all.catalog.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CountryRepository extends JpaRepository<Country, Long> {

    Optional<Country> findByIso2CodeIgnoreCase(String iso2Code);
    Optional<Country> findByIso3CodeIgnoreCase(String iso3Code);
}