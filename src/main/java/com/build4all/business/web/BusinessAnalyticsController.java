package com.build4all.business.web;

import com.build4all.business.dto.BusinessAnalytics;
import com.build4all.security.JwtUtil;
import com.build4all.business.service.BusinessAnalyticsService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Business Analytics API", description = "Endpoints for generating business analytics insights")
public class BusinessAnalyticsController {
	
	@Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BusinessAnalyticsService analyticsService;

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    	@GetMapping("/business/{businessId}/insights")
    	public ResponseEntity<?> getBusinessInsights(
    	    @Parameter(description = "ID of the business to retrieve analytics for")
    	    @PathVariable Long businessId,
    	    @RequestHeader("Authorization") String authHeader) {

    	    try {
    	        String token = authHeader.replace("Bearer ", "").trim();

    	        if (!(jwtUtil.isAdminToken(token) || jwtUtil.isBusinessToken(token))) {
    	            return ResponseEntity.status(HttpStatus.FORBIDDEN)
    	                    .body(Map.of("message", "Forbidden: Admin or Business access required"));
    	        }

    	        BusinessAnalytics analytics = analyticsService.getAnalyticsForBusiness(businessId);
    	        if (analytics == null) {
    	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Analytics not found"));
    	        }

    	        return ResponseEntity.ok(analytics);

    	    } catch (Exception e) {
    	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    	                .body(Map.of("message", "Internal server error", "error", e.getMessage()));
    	    }
    	}

}
