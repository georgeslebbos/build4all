package com.build4all.catalog.web;

import com.build4all.catalog.dto.CurrencyRequest;

import com.build4all.catalog.service.CurrencyService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.build4all.settings.domain.AppSettings;
import com.build4all.catalog.domain.Currency;
import com.build4all.settings.repository.AppSettingsRepository;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    @Autowired
    private CurrencyRepository currencyRepository;
    
    @Autowired
    private AppSettingsRepository appSettingsRepository;

    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private CurrencyService currencyService;


    private boolean isAuthorized(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;

        String jwt = token.substring(7).trim();

        // ✅ Business token check
        if (jwtUtil.isBusinessToken(jwt)) return true;

        // ✅ Admin role check
        String role = jwtUtil.extractRole(jwt);
        if ("SUPER_ADMIN".equals(role) || "MANAGER".equals(role)) return true;

        // ✅ User token check
        return jwtUtil.isUserToken(jwt);
    }


//ADDED
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentCurrency(@RequestHeader(value = "Authorization", required = false) String token) {
        // If token is present but invalid, reject
        if (token != null && !isAuthorized(token) && token== null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied. Invalid token.");
        }

        // Always ensure currencies exist
        currencyService.ensureDefaultCurrencies();

        AppSettings settings = appSettingsRepository.findById(1L).orElse(null);
        if (settings == null || settings.getCurrency() == null) {
            return ResponseEntity.ok("CAD"); // fallback if not set
        }

        return ResponseEntity.ok(settings.getCurrency().getCurrencyType());
    }


    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/chooseCurrency")
    public ResponseEntity<?> chooseCurrency(
            @RequestHeader("Authorization") String token,
            @RequestBody CurrencyRequest request) {

        // Token check already present in main code, so no change needed here
        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied.");
        }

        // Existing code unchanged
        currencyService.ensureDefaultCurrencies();

        String type = request.getCurrencyType() != null ? request.getCurrencyType().toUpperCase() : "CAD";

        Optional<Currency> selectedCurrency = currencyRepository.findByCurrencyType(type);

        if (selectedCurrency.isPresent()) {
            AppSettings settings = appSettingsRepository.findById(1L)
                    .orElse(new AppSettings());

            settings.setCurrency(selectedCurrency.get());
            appSettingsRepository.save(settings);

            return ResponseEntity.ok(selectedCurrency.get());
        } else {
            return ResponseEntity.badRequest().body("Currency type not found in the database.");
        }
    }

}