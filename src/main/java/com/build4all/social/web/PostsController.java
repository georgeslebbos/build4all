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
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401"),
        @ApiResponse(responseCode = "500")
    })
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createPost(@RequestParam(required = false) String content,
                                        @RequestParam(required = false) MultipartFile image,
                                        @RequestParam(required = false) String hashtags,
                                        @RequestParam(required = false, defaultValue = "PUBLIC") String visibility,
                                        @RequestParam Long ownerProjectLinkId,
                                        Principal principal,
                                        @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);

        PostVisibility postVisibility = postVisibilityRepository.findByName(visibility.toUpperCase())
                .orElseGet(() -> postVisibilityRepository.findByName("PUBLIC").orElse(null));
        if (postVisibility == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Visibility setting error");
        }

        Posts created = postsService.createPost(content, image, hashtags, user, postVisibility, /*owner scope*/ ownerProjectLinkId);
        return ResponseEntity.ok(new PostDto(created, user.getId()));
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @GetMapping
    public ResponseEntity<List<PostDto>> getAllPosts(Principal principal,
                                                     @RequestParam Long ownerProjectLinkId,
                                                     @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        if (principal == null) return ResponseEntity.status(401).build();

        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        List<PostDto> postDtos = postsService.getAllPostDtos(user.getId());
        return ResponseEntity.ok(postDtos);
    }

    /** Optional owner-scoped feed (pass ownerProjectLinkId). */
    @GetMapping("/by-owner")
    public ResponseEntity<?> getOwnerFeed(@RequestParam Long ownerProjectLinkId,
                                          Principal principal,
                                          @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        return ResponseEntity.ok(postsService.getOwnerFeed(ownerProjectLinkId, user.getId()));
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId,
                                             @RequestParam Long ownerProjectLinkId,
                                             Principal principal,
                                             @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        Users user = usersService.getUserByEmaill(principal.getName(), ownerProjectLinkId);
        postsService.deletePost(postId, user);
        return ResponseEntity.ok("Post deleted successfully");
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDto>> getPostsByUser(@PathVariable Long userId,
                                                        @RequestParam Long ownerProjectLinkId,
                                                        @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        // still scoped by ownerProjectLinkId to prevent cross-tenant access
        Users viewer = usersService.getUserByEmaill(jwtUtil.extractUsername(authHeader.substring(7)), ownerProjectLinkId);
        List<PostDto> postDtos = postsService.getPostDtosByUser(userId);
        return ResponseEntity.ok(postDtos);
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "403"),
        @ApiResponse(responseCode = "401")
    })
    @DeleteMapping("/{postId}/user/{userId}")
    public ResponseEntity<?> deletePostByUser(@PathVariable Long postId,
                                              @PathVariable Long userId,
                                              @RequestParam Long ownerProjectLinkId,
                                              @RequestHeader("Authorization") String authHeader) {
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        // optional: verify caller belongs to same ownerProjectLinkId as userId if your service enforces it
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
