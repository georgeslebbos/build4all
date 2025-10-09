package com.build4all.business.web;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import com.build4all.business.repository.BusinessStatusRepository;
import com.build4all.security.JwtUtil;
import com.build4all.business.service.BusinessService;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import org.springframework.beans.factory.annotation.Value;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/businesses")
@Tag(name = "Business Management", description = "Operations for creating, reading, updating, and deleting businesses")
public class BusinessController {

    @Autowired
    private BusinessService businessService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BusinessStatusRepository businessStatusRepository;
    
    @Value("${app.base-domain}")
    private String baseDomain;

    
    private boolean isAuthorized(String token, Long targetBusinessId) {
        if (token == null || !token.startsWith("Bearer ")) return false;
        String jwt = token.substring(7);
        String email = jwtUtil.extractUsername(jwt);
        String role = jwtUtil.extractRole(jwt);
        if ("SUPER_ADMIN".equals(role)) return true;

        Businesses business = businessService.findByEmail(email); 
        return business != null && business.getId().equals(targetBusinessId);
    }


    private boolean isBusinessToken(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;
        String jwt = token.substring(7);
        String email = jwtUtil.extractUsername(jwt);
        Businesses business = businessService.findByEmail(email);
        return business != null;
    }

    @Operation(summary = "Get a business by ID")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @GetMapping("/{id}")
    public ResponseEntity<?> getBusinessById(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
    	 String token = authHeader.replace("Bearer ", "").trim();
    	    if (!(jwtUtil.isBusinessToken(token) || jwtUtil.isAdminToken(token) || jwtUtil.isUserToken(token))) {
    	        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business, Admin or User token required");
    	    }

    	    if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied");
    	    Businesses business = businessService.findById(id);
    	    return business != null ? ResponseEntity.ok(business) : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
     
    }

    @Operation(summary = "Get all businesses")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @GetMapping
    public ResponseEntity<?> getAllBusinesses(@RequestHeader("Authorization") String authHeader) {
        String role = jwtUtil.extractRole(authHeader.replace("Bearer ", ""));
        if (!"SUPER_ADMIN".equals(role)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Only SUPER_ADMIN can access this");
        return ResponseEntity.ok(businessService.findAll());
    }

    @Operation(summary = "Update a business")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBusiness(@PathVariable Long id, @RequestBody Businesses business, @RequestHeader("Authorization") String authHeader) {
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        business.setId(id);
        return ResponseEntity.ok(businessService.save(business));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBusinessWithPassword(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        String token = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business token required");
        }

        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String password = request.get("password");
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body("Password is required");
        }

        boolean deleted = businessService.deleteBusinessByIdWithPassword(id, password);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Business deleted successfully."));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Incorrect password or business not found");
        }
    }

    @Operation(summary = "Update business with images")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/update-with-images/{id}")
    public ResponseEntity<?> updateBusinessWithImages(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String name,
            @RequestParam (required = false)String email,
            @RequestParam(required = false) String password,
            @RequestParam String description,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String websiteUrl,
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(required = false) MultipartFile banner) {
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        try {
            Businesses updated = businessService.updateBusinessWithImages(id, name, email, password, description, phoneNumber, websiteUrl, logo, banner);
            return ResponseEntity.ok(updated);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload error");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Update logo and banner")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/update-logo-banner/{id}")
    public ResponseEntity<?> updateBusinessLogoAndBanner(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(required = false) MultipartFile banner) {
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        Businesses existing = businessService.findById(id);
        
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");
        if (logo != null && !logo.isEmpty()) existing.setBusinessLogoUrl(logo.getOriginalFilename());
        if (banner != null && !banner.isEmpty()) existing.setBusinessBannerUrl(banner.getOriginalFilename());
        return ResponseEntity.ok(businessService.save(existing));
    }

    @Operation(summary = "Request password reset")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> sendBusinessResetCode(
            @RequestBody Map<String, String> request) {

        boolean success = businessService.resetPassword(request.get("email"));

        return success
                ? ResponseEntity.ok(Map.of("message", "Reset code sent"))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Business not found"));
    }


    @Operation(summary = "Verify reset code")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyBusinessResetCode(
            @RequestBody Map<String, String> request) {

        boolean verified = businessService.verifyResetCode(request.get("email"), request.get("code"));
        
        return verified
                ? ResponseEntity.ok(Map.of("message", "Code verified"))
                : ResponseEntity.badRequest().body(Map.of("message", "Invalid code"));
    }


    @Operation(summary = "Update business password")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/update-password")
    public ResponseEntity<Map<String, String>> updateBusinessPassword(
            @RequestBody Map<String, String> request) {

        String email = request.get("email");
        String newPassword = request.get("newPassword");

        if (email == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing email or password"));
        }

        boolean updated = businessService.updatePasswordDirectly(email, newPassword);
        return updated
                ? ResponseEntity.ok(Map.of("message", "Password updated"))
                : ResponseEntity.badRequest().body(Map.of("message", "Business not found"));
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
    @DeleteMapping("/delete-logo/{id}")
    public ResponseEntity<?> deleteLogo(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business token required");
        }

        boolean deleted = businessService.deleteBusinessLogo(id);
        if (deleted) {
            return ResponseEntity.ok("Logo deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No logo found to delete.");
        }
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
    @DeleteMapping("/delete-banner/{id}")
    public ResponseEntity<?> deleteBanner(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business token required");
        }

        boolean deleted = businessService.deleteBusinessBanner(id);
        if (deleted) {
            return ResponseEntity.ok("Banner deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No banner found to delete.");
        }
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
    @GetMapping("/public")
    public ResponseEntity<List<Businesses>> getPublicActiveBusinesses(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "").trim();

        if (!(jwtUtil.isUserToken(token) || jwtUtil.isAdminToken(token))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Businesses> businesses = businessService.getAllPublicActiveBusinesses();
        return ResponseEntity.ok(businesses);
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
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateBusinessStatus(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String newStatus = request.get("status");
        String password = request.get("password");

        if (newStatus == null) {
            return ResponseEntity.badRequest().body("Missing status");
        }

        if ("INACTIVE".equalsIgnoreCase(newStatus)) {
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Password is required to deactivate account.");
            }

            boolean isPasswordValid = businessService.verifyPassword(id, password);
            if (!isPasswordValid) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password. Status not changed.");
            }
        }

        try {
            Businesses business = businessService.findById(id);
            BusinessStatus statusEntity = businessStatusRepository.findByName(newStatus.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid status value"));
            
            business.setStatus(statusEntity);
            return ResponseEntity.ok(businessService.save(business));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status value");
        }
    }


    @Operation(summary = "Toggle public profile visibility")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PutMapping("/{id}/visibility")
    public ResponseEntity<?> updateBusinessVisibility(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Boolean> request) {

        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        Boolean isPublic = request.get("isPublicProfile");
        if (isPublic == null) {
            return ResponseEntity.badRequest().body("Missing isPublicProfile value");
        }

        Businesses business = businessService.findById(id);
        business.setIsPublicProfile(isPublic);
        return ResponseEntity.ok(businessService.save(business));
    }

    
    @Operation(summary = "Send Manager Invitation Email")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/{id}/send-manager-invite")
    public ResponseEntity<?> sendManagerInvite(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }

        Businesses business = businessService.findById(id);
        if (business == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");
        }

        try {
            businessService.sendManagerInvite(email, business);
            return ResponseEntity.ok(Map.of("message", "Invitation sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send invite: " + e.getMessage()));
        }
    }

    @Operation(summary = "Register new manager from invite")
    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/register-manager")
    public ResponseEntity<?> registerManagerFromInvite(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String username = request.get("username");
            String firstName = request.get("firstName");
            String lastName = request.get("lastName");
            String password = request.get("password");

            if (token == null || username == null || firstName == null || lastName == null || password == null) {
                return ResponseEntity.badRequest().body("Missing required fields");
            }

            boolean success = businessService.registerManagerFromInvite(
                token, username, firstName, lastName, password
            );

            if (success) {
                return ResponseEntity.ok(Map.of("message", "Manager registered successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body(Map.of("error", "Invalid token or already used"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Map.of("error", "Failed to register manager: " + e.getMessage()));
        }
    }


    @PostMapping("/stripe/connect")
    public ResponseEntity<?> connectStripeAccount(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(jwt);
            Businesses business = businessService.findByEmail(email);
            if (business == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));
            }

            String accountId = business.getStripeAccountId();

            
            if (accountId == null) {
                Account account = Account.create(AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.STANDARD)
                    .build());
                accountId = account.getId();
                business.setStripeAccountId(accountId);
                businessService.save(business);
            }

          
            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setRefreshUrl("https://localhost:8080/stripe/refresh") 
                .setReturnUrl("https://localhost:8080/stripe/success")
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build());

            return ResponseEntity.ok(Map.of(
                "url", link.getUrl(),
                "accountId", accountId
            ));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Check if business has connected Stripe account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Business not found"),
        @ApiResponse(responseCode = "500", description = "Stripe error")
    })
    @GetMapping("/{id}/stripe-status")
    public ResponseEntity<?> checkStripeConnection(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Businesses business = businessService.findById(id);
        if (business == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));
        }

        String stripeAccountId = business.getStripeAccountId();
        if (stripeAccountId == null || stripeAccountId.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("stripeConnected", false);
            response.put("detailsSubmitted", false);
            response.put("chargesEnabled", false);
            response.put("stripeAccountId", null); // ✅ null allowed in HashMap
            return ResponseEntity.ok(response);
        }


        try {
            Account account = Account.retrieve(stripeAccountId);
            boolean chargesEnabled = account.getChargesEnabled();
            boolean detailsSubmitted = account.getDetailsSubmitted();

            return ResponseEntity.ok(Map.of(
                "stripeConnected", chargesEnabled && detailsSubmitted,
                "detailsSubmitted", detailsSubmitted,
                "chargesEnabled", chargesEnabled,
                "stripeAccountId", stripeAccountId
            ));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }

    
    @Operation(summary = "Resume Stripe onboarding for incomplete account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Onboarding link created"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Business not found or no Stripe ID"),
        @ApiResponse(responseCode = "500", description = "Stripe error")
    })
    @PostMapping("/stripe/resume")
    public ResponseEntity<?> resumeStripeOnboarding(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(jwt);
            Businesses business = businessService.findByEmail(email);

            if (business == null || business.getStripeAccountId() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No Stripe account found for this business"));
            }

            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                .setAccount(business.getStripeAccountId())
                .setRefreshUrl("https://localhost:8080/stripe/refresh")
                .setReturnUrl("https://localhost:8080/stripe/success")
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build());

            return ResponseEntity.ok(Map.of("url", link.getUrl()));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }



}