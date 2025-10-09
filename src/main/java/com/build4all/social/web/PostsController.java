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
    private final JwtUtil jwtUtil;  // Your JWT validation utility

    public PostsController(PostService postsService, UserService usersService, PostVisibilityRepository postVisibilityRepository, JwtUtil jwtUtil) {
        this.postsService = postsService;
        this.usersService = usersService;
        this.postVisibilityRepository = postVisibilityRepository;
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
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createPost(@RequestParam(required = false) String content,
                                        @RequestParam(required = false) MultipartFile image,
                                        @RequestParam(required = false) String hashtags,
                                        @RequestParam(required = false, defaultValue = "PUBLIC") String visibility,
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

        Posts created = postsService.createPost(content, image, hashtags, user, postVisibility);

        return ResponseEntity.ok(new PostDto(created, user.getId()));
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
    @GetMapping
    public ResponseEntity<List<PostDto>> getAllPosts(Principal principal,
                                                     @RequestHeader("Authorization") String authHeader) {

        // Token validation
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Users user = usersService.getUserByEmaill(principal.getName());
            List<PostDto> postDtos = postsService.getAllPostDtos(user.getId());
            return ResponseEntity.ok(postDtos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
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
    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId,
                                             Principal principal,
                                             @RequestHeader("Authorization") String authHeader) {

        // Token validation
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        Users user = usersService.getUserByEmaill(principal.getName());
        postsService.deletePost(postId, user);
        return ResponseEntity.ok("Post deleted successfully");
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
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDto>> getPostsByUser(@PathVariable Long userId,
                                                       @RequestHeader("Authorization") String authHeader) {

        // Token validation
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return ResponseEntity.status(tokenCheck.getStatusCode()).build();

        List<PostDto> postDtos = postsService.getPostDtosByUser(userId);
        return ResponseEntity.ok(postDtos);
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
    @DeleteMapping("/{postId}/user/{userId}")
    public ResponseEntity<?> deletePostByUser(@PathVariable Long postId,
                                              @PathVariable Long userId,
                                              @RequestHeader("Authorization") String authHeader) {

        // Token validation
        ResponseEntity<String> tokenCheck = validateUserToken(authHeader);
        if (tokenCheck != null) return tokenCheck;

        boolean deleted = postsService.deletePostByUser(postId, userId);
        if (deleted) {
            return ResponseEntity.ok("Post deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not allowed to delete this post.");
        }
    }


    // Helper method for token validation to avoid repeating code
    private ResponseEntity<String> validateUserToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.isUserToken(token)) {  // Your JWT validation method to check if token belongs to a user
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user token");
        }

        return null; // token is valid
    }
}
