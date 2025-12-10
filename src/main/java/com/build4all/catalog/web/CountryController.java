package com.build4all.catalog.web;

import com.build4all.catalog.domain.Country;
import com.build4all.catalog.repository.CountryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Simple read-only controller for Countries.
 * Currently only exposes "get all countries" endpoint.
 * No service layer is used – we inject the repository directly.
 */
@RestController
@RequestMapping("/api/countries")
public class CountryController {

    private final CountryRepository countryRepository;

    public CountryController(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @Operation(
            summary = "Get all countries",
            description = "Returns the full list of countries configured in the catalog."
    )
    @ApiResponse(responseCode = "200", description = "List of countries")
    @GetMapping
    public ResponseEntity<List<Country>> getAllCountries() {
        List<Country> countries = countryRepository.findAll();
        return ResponseEntity.ok(countries);
    }
}
