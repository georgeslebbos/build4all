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
    private BusinessService businessService;

    private final BusinessUserService service;
    private final JwtUtil jwtUtil;
    
    private boolean isBusinessToken(String token) {
        try {
            String jwt = token.replace("Bearer ", "").trim();
            return jwtUtil.isBusinessToken(jwt);
        } catch (Exception e) {
            return false;
        }
    }


    public BusinessUserController(BusinessUserService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }
    

    @PostMapping("/create")
    public ResponseEntity<?> createUser(@RequestHeader("Authorization") String token,
                                        @RequestBody BusinessUserDto dto) {
        if (!isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied: Business token required");
        }

   
        Long businessId = jwtUtil.extractId(token.replace("Bearer ", "").trim());

        BusinessUser user = service.createBusinessUser(businessId, dto);
        return ResponseEntity.ok(user);
    }


    @GetMapping("/my-users")
    public ResponseEntity<?> getMyUsers(@RequestHeader("Authorization") String token) {
        if (!isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business token required");
        }

        Long businessId = jwtUtil.extractId(token.replace("Bearer ", ""));
        return ResponseEntity.ok(service.getUsersByBusiness(businessId));
    }
    
    
 


    
    
}
