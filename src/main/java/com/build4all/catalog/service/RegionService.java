package com.build4all.catalog.service;

import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.dto.RegionDto;
import com.build4all.catalog.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegionService {

    private final RegionRepository regionRepository;

    public RegionService(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @Transactional(readOnly = true)
    public List<RegionDto> getAllRegions() {
        List<Region> regions = regionRepository.findAllWithCountry();

        return regions.stream()
                .map(this::toDto)
                .toList();
    }

    private RegionDto toDto(Region r) {
        Country c = r.getCountry(); // safe because we used join fetch

        return new RegionDto(
                r.getId(),
                r.getCode(),
                r.getName(),
                r.isActive(),
                c != null ? c.getId() : null,
                c != null ? c.getIso2Code() : null,
                c != null ? c.getIso3Code() : null,
                c != null ? c.getName() : null
        );
    }
}
