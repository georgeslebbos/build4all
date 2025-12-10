package com.build4all.catalog.web;

import com.build4all.catalog.dto.RegionDto;
import com.build4all.catalog.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @Operation(
            summary = "Get all regions",
            description = "Returns the full list of regions configured in the catalog."
    )
    @ApiResponse(responseCode = "200", description = "List of regions")
    @GetMapping
    public ResponseEntity<List<RegionDto>> getAllRegions() {
        return ResponseEntity.ok(regionService.getAllRegions());
    }
}
