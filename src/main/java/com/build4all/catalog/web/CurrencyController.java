package com.build4all.catalog.web;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.dto.CurrencyRequest;
import com.build4all.catalog.dto.CurrencyDTO;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.service.CurrencyService;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.security.JwtUtil;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

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
    private CurrencyService currencyService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AdminUserProjectRepository adminUserProjectRepository; // 👈 per-app currency

    // --------------- DTO MAPPER ---------------

    private CurrencyDTO toDto(Currency c) {
        return new CurrencyDTO(
                c.getId(),
                c.getCurrencyType(),
                c.getCode(),
                c.getSymbol()
        );
    }

    // --------------- AUTH HELPER ---------------

    private boolean isAuthorized(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;

        String jwt = token.substring(7).trim();

        // Business token check
        if (jwtUtil.isBusinessToken(jwt)) return true;

        // Admin role check
        String role = jwtUtil.extractRole(jwt);
        if ("SUPER_ADMIN".equals(role) || "MANAGER".equals(role)) return true;

        // User token check
        return jwtUtil.isUserToken(jwt);
    }

    // --------------- GET CURRENCY BY ID (for dart-define id) ---------------

    @GetMapping("/{id}")
    public ResponseEntity<CurrencyDTO> getById(@PathVariable Long id) {
        return currencyRepository.findById(id)
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    // --------------- GET CURRENT CURRENCY FOR ONE APP ---------------

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
    public ResponseEntity<?> getCurrentCurrency(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam("appSlug") String appSlug) {

        // If token is present but invalid → reject
        if (token != null && !isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Access denied. Invalid token.");
        }

        // Always ensure default currencies exist (seed DB)
        currencyService.ensureDefaultCurrencies();

        AdminUserProject app = adminUserProjectRepository.findBySlug(appSlug)
                .orElseThrow(() -> new IllegalArgumentException("App not found for slug: " + appSlug));

        Currency c = app.getCurrency();
        if (c == null) {
            // fallback if not set for this app
            return ResponseEntity.ok("CAD");
        }

        // you can also return c.getCode() if you prefer
        return ResponseEntity.ok(c.getCurrencyType());
    }

    // --------------- SET CURRENCY FOR ONE APP ---------------

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
            @RequestParam("appSlug") String appSlug,
            @RequestBody CurrencyRequest request) {

        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied.");
        }

        // Make sure defaults exist
        currencyService.ensureDefaultCurrencies();

        String type = request.getCurrencyType() != null
                ? request.getCurrencyType().toUpperCase()
                : "CAD";

        Optional<Currency> selectedCurrency = currencyRepository.findByCurrencyType(type);

        if (selectedCurrency.isEmpty()) {
            return ResponseEntity.badRequest().body("Currency type not found in the database.");
        }

        AdminUserProject app = adminUserProjectRepository.findBySlug(appSlug)
                .orElseThrow(() -> new IllegalArgumentException("App not found for slug: " + appSlug));

        // Set per-app currency
        app.setCurrency(selectedCurrency.get());
        adminUserProjectRepository.save(app);

        return ResponseEntity.ok(toDto(selectedCurrency.get()));
    }
    
    
 // --------------- LIST ALL CURRENCIES (for dropdown) ---------------
    @GetMapping
    public ResponseEntity<?> listCurrencies(
           
    ) {
       

        // Ensure defaults exist
        currencyService.ensureDefaultCurrencies();

        // Return all currencies as DTOs (sorted nicely)
        var list = currencyRepository.findAll().stream()
                .sorted((a, b) -> {
                    String aa = (a.getCurrencyType() == null ? "" : a.getCurrencyType());
                    String bb = (b.getCurrencyType() == null ? "" : b.getCurrencyType());
                    return aa.compareToIgnoreCase(bb);
                })
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(list);
    }

}
