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

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createPost(@RequestParam(required = false) String content,
                                        @RequestParam(required = false) MultipartFile image,
                                        @RequestParam(required = false) String hashtags,
                                        @RequestParam(required = false, defaultValue = "PUBLIC") String visibility,
                                        @RequestParam Long adminId,
                                        @RequestParam Long projectId,
                                        Principal principal,
                                        @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);

        PostVisibility postVisibility = postVisibilityRepository.findByName(visibility.toUpperCase())
                .orElseGet(() -> postVisibilityRepository.findByName("PUBLIC").orElse(null));
        if (postVisibility == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Visibility setting error");
        }

        Posts created = postsService.createPost(content, image, hashtags, user, postVisibility, /*owner scope*/ null);
        return ResponseEntity.ok(new PostDto(created, user.getId()));
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ResponseEntity<List<PostDto>> getAllPosts(Principal principal,
                                                     @RequestParam Long adminId,
                                                     @RequestParam Long projectId,
                                                     @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        if (principal == null) return ResponseEntity.status(401).build();

        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        List<PostDto> postDtos = postsService.getAllPostDtos(user.getId());
        return ResponseEntity.ok(postDtos);
    }

    /** Optional owner-scoped feed if you still need it (pass a collection owner id). */
    @GetMapping("/by-owner")
    public ResponseEntity<?> getOwnerFeed(@RequestParam Long aupId,
                                          @RequestParam Long adminId,
                                          @RequestParam Long projectId,
                                          Principal principal,
                                          @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        if (principal == null) return ResponseEntity.status(401).build();

        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        return ResponseEntity.ok(postsService.getOwnerFeed(aupId, user.getId()));
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId,
                                             @RequestParam Long adminId,
                                             @RequestParam Long projectId,
                                             Principal principal,
                                             @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        Users user = usersService.getUserByEmaill(principal.getName(), adminId, projectId);
        postsService.deletePost(postId, user);
        return ResponseEntity.ok("Post deleted successfully");
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDto>> getPostsByUser(@PathVariable Long userId,
                                                        @RequestParam Long adminId,
                                                        @RequestParam Long projectId,
                                                        @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        // NOTE: listing user posts is not sensitive to the caller’s app user identity here,
        // but still require adminId/projectId for consistent scoping.
        List<PostDto> postDtos = postsService.getPostDtosByUser(userId);
        return ResponseEntity.ok(postDtos);
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/{postId}/user/{userId}")
    public ResponseEntity<?> deletePostByUser(@PathVariable Long postId,
                                              @PathVariable Long userId,
                                              @RequestParam Long adminId,
                                              @RequestParam Long projectId,
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

    private ResponseEntity<String> validateUserToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.isUserToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user token");
        }
        return null;
    }
}
