package com.build4all.business.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.build4all.business.dto.BusinessUserDto;
import com.build4all.business.domain.BusinessUser;
import com.build4all.security.JwtUtil;
import com.build4all.business.service.BusinessService;
import com.build4all.business.service.BusinessUserService;

@RestController
@RequestMapping("/api/business-users")
public class BusinessUserController {

    @Autowired
    private BusinessService businessService; // Currently not used in this controller (can be removed if not needed)

    private final BusinessUserService service; // Service responsible for creating/fetching business users
    private final JwtUtil jwtUtil;             // JWT helper for token validation and extracting businessId

    /**
     * Helper: ensures the caller is authenticated with a BUSINESS token.
     * We accept the raw Authorization header, strip "Bearer ", then validate token type.
     */
    private boolean isBusinessToken(String token) {
        try {
            String jwt = token.replace("Bearer ", "").trim();
            return jwtUtil.isBusinessToken(jwt);
        } catch (Exception e) {
            // Any parsing/extraction exception means we treat it as invalid/unauthorized
            return false;
        }
    }

    public BusinessUserController(BusinessUserService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Create a new BusinessUser under the currently authenticated business.
     * The businessId is taken from the JWT token (business self context).
     */
    @PostMapping("/create")
    public ResponseEntity<?> createUser(
            @RequestHeader("Authorization") String token,
            @RequestBody BusinessUserDto dto
    ) {
        // Authorization: only business tokens can create business users
        if (!isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied: Business token required");
        }

        // Extract businessId from JWT (make sure extractId() returns business id for business tokens)
        Long businessId = jwtUtil.extractId(token.replace("Bearer ", "").trim());

        // Delegate creation logic to service (includes validation + setting business relationship)
        BusinessUser user = service.createBusinessUser(businessId, dto);
        return ResponseEntity.ok(user);
    }

    /**
     * Get all BusinessUsers belonging to the currently authenticated business.
     * Useful for "My Team" / "My Employees" screen in business dashboard.
     */
    @GetMapping("/my-users")
    public ResponseEntity<?> getMyUsers(@RequestHeader("Authorization") String token) {
        // Authorization: only business tokens can view their users list
        if (!isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied: Business token required");
        }

        // NOTE: In the original code you did not trim here; trimming avoids token parsing issues
        Long businessId = jwtUtil.extractId(token.replace("Bearer ", "").trim());

        // Return lightweight DTO list (BusinessUserSimpleDto) created by service layer
        return ResponseEntity.ok(service.getUsersByBusiness(businessId));
    }
}
