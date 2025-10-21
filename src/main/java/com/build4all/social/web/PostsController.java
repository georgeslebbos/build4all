package com.build4all.social.web;

import com.build4all.social.dto.PostDto;
import com.build4all.social.domain.Posts;
import com.build4all.user.domain.Users;
import com.build4all.social.domain.PostVisibility;
import com.build4all.social.service.PostService;
import com.build4all.user.service.UserService;
import com.build4all.social.repository.PostVisibilityRepository;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.List;

/**
 * Social posts REST endpoints. Backward-compatible with optional owner scoping.
 */
@RestController
@RequestMapping("/api/posts")
public class PostsController {

    private final PostService postsService;
    private final UserService usersService;
    private final PostVisibilityRepository postVisibilityRepository;
    private final JwtUtil jwtUtil;

    public PostsController(PostService postsService,
                           UserService usersService,
                           PostVisibilityRepository postVisibilityRepository,
                           JwtUtil jwtUtil) {
        this.postsService = postsService;
        this.usersService = usersService;
        this.postVisibilityRepository = postVisibilityRepository;
        this.jwtUtil = jwtUtil;
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
        @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
        @ApiResponse(responseCode = "402", description = "Payment Required – Reserved"),
        @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createPost(@RequestParam(required = false) String content,
                                        @RequestParam(required = false) MultipartFile image,
                                        @RequestParam(required = false) String hashtags,
                                        @RequestParam(required = false, defaultValue = "PUBLIC") String visibility,
                                        @RequestParam(required = false) Long aupId, // OPTIONAL owner scope
                                        Principal principal,
                                        @RequestHeader("Authorization") String authHeader) {

        // Token validations
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        Users user = usersService.getUserByEmaill(principal.getName());

        PostVisibility postVisibility = postVisibilityRepository.findByName(visibility.toUpperCase())
                .orElseGet(() -> postVisibilityRepository.findByName("PUBLIC").orElse(null));

        if (postVisibility == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Visibility setting error");
        }

        Posts created = postsService.createPost(content, image, hashtags, user, postVisibility, aupId);
        return ResponseEntity.ok(new PostDto(created, user.getId()));
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<PostDto>> getAllPosts(Principal principal,
                                                     @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        if (principal == null) return ResponseEntity.status(401).build();

        try {
            Users user = usersService.getUserByEmaill(principal.getName());
            List<PostDto> postDtos = postsService.getAllPostDtos(user.getId());
            return ResponseEntity.ok(postDtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /** NEW: owner-scoped feed (optional). */
    @GetMapping("/by-owner")
    public ResponseEntity<?> getOwnerFeed(@RequestParam Long aupId,
                                          Principal principal,
                                          @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        if (principal == null) return ResponseEntity.status(401).build();

        Users user = usersService.getUserByEmaill(principal.getName());
        return ResponseEntity.ok(postsService.getOwnerFeed(aupId, user.getId()));
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId,
                                             Principal principal,
                                             @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        Users user = usersService.getUserByEmaill(principal.getName());
        postsService.deletePost(postId, user);
        return ResponseEntity.ok("Post deleted successfully");
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDto>> getPostsByUser(@PathVariable Long userId,
                                                        @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        List<PostDto> postDtos = postsService.getPostDtosByUser(userId);
        return ResponseEntity.ok(postDtos);
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @DeleteMapping("/{postId}/user/{userId}")
    public ResponseEntity<?> deletePostByUser(@PathVariable Long postId,
                                              @PathVariable Long userId,
                                              @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        boolean deleted = postsService.deletePostByUser(postId, userId);
        if (deleted) {
            return ResponseEntity.ok("Post deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not allowed to delete this post.");
        }
    }

    // Shared helper to validate Bearer token
    private ResponseEntity<String> validateUserToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user token");
        }
        return null; // valid
    }
}
