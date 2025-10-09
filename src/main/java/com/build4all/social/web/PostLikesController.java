package com.build4all.social.web;

import com.build4all.user.domain.Users;
import com.build4all.security.JwtUtil;
import com.build4all.social.service.PostLikesService;
import com.build4all.user.service.UserService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/posts")
public class PostLikesController {
	

    private final PostLikesService likesService;
    private final UserService usersService;
    private final JwtUtil jwtUtil; // Assuming you have a JWT utility class for token validation

    public PostLikesController(PostLikesService likesService, UserService usersService, JwtUtil jwtUtil) {
        this.likesService = likesService;
        this.usersService = usersService;
        this.jwtUtil = jwtUtil;
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
    @PostMapping("/{postId}/like")
    public ResponseEntity<String> toggleLike(
            @PathVariable Long postId,
            Principal principal,
            @RequestHeader("Authorization") String authHeader) {

        // Token validation
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user token");
        }

        // Original code unchanged
        Users user = usersService.getUserByEmaill(principal.getName());
        String result = likesService.toggleLike(postId, user);

        return ResponseEntity.ok(result);
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
    @GetMapping("/{postId}/likes")
    public ResponseEntity<Long> countLikes(
            @PathVariable Long postId,
            @RequestHeader("Authorization") String authHeader) {

        // Token validation
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Original code unchanged
        long count = likesService.countLikes(postId);
        return ResponseEntity.ok(count);
    }
}
