package com.build4all.social.web;

import com.build4all.social.dto.CommentDto;
import com.build4all.social.domain.Comments;
import com.build4all.user.domain.Users;
import com.build4all.security.JwtUtil;
import com.build4all.social.service.CommentsService;
import com.build4all.user.service.UserService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments")
public class CommentsController {

    private final CommentsService commentsService;
    private final UserService usersService;
    private final JwtUtil jwtUtil;

    public CommentsController(CommentsService commentsService, UserService usersService, JwtUtil jwtUtil) {
        this.commentsService = commentsService;
        this.usersService = usersService;
        this.jwtUtil = jwtUtil;
    }

    private Users getUserFromToken(String token) {
        if (token == null || !token.startsWith("Bearer ")) throw new RuntimeException("Missing or invalid Authorization header");
        String jwt = token.substring(7);
        String email = jwtUtil.extractUsername(jwt);
        return usersService.getUserByEmaill(email);
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
    @PostMapping("/{postId}")
    public CommentDto addComment(@PathVariable Long postId,
                                 @RequestParam String content,
                                 @RequestHeader("Authorization") String authHeader) {
        Users user = getUserFromToken(authHeader);
        Comments comment = commentsService.addComment(postId, content, user);
        return new CommentDto(comment, user.getId());
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
    @GetMapping("/{postId}")
    public ResponseEntity<List<CommentDto>> getComments(@PathVariable Long postId,
                                                        @RequestHeader("Authorization") String authHeader) {
        Users user = getUserFromToken(authHeader);
        List<Comments> comments = commentsService.getCommentsByPost(postId);
        List<CommentDto> dtos = comments.stream()
                                        .map(c -> new CommentDto(c, user.getId()))
                                        .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
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
    @DeleteMapping("/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable Long commentId,
                                                @RequestHeader("Authorization") String authHeader) {
        Users user = getUserFromToken(authHeader);
        commentsService.deleteComment(commentId, user);
        return ResponseEntity.ok("Comment deleted successfully");
    }
}
