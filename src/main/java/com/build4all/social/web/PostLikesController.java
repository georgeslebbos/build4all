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
    private final JwtUtil jwtUtil;

    public PostLikesController(PostLikesService likesService, UserService usersService, JwtUtil jwtUtil) {
        this.likesService = likesService;
        this.usersService = usersService;
        this.jwtUtil = jwtUtil;
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @PostMapping("/{postId}/like")
    public ResponseEntity<String> toggleLike(@PathVariable Long postId,
                                             @RequestParam Long ownerProjectLinkId,
                                             Principal principal,
                                             @RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user token");
        }

        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        String result = likesService.toggleLike(postId, user);
        return ResponseEntity.ok(result);
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @GetMapping("/{postId}/likes")
    public ResponseEntity<Long> countLikes(@PathVariable Long postId,
                                           @RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        long count = likesService.countLikes(postId);
        return ResponseEntity.ok(count);
    }
}
