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

    private Users getUserFromToken(String authHeader, Long ownerProjectLinkId) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new RuntimeException("Missing or invalid Authorization header");
        String jwt = authHeader.substring(7);
        String emailOrPhone = jwtUtil.extractUsername(jwt);
        return usersService.getUserByEmaill(emailOrPhone, ownerProjectLinkId);
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @PostMapping("/{postId}")
    public CommentDto addComment(@PathVariable Long postId,
                                 @RequestParam String content,
                                 @RequestParam Long ownerProjectLinkId,
                                 @RequestHeader("Authorization") String authHeader) {
        Users user = getUserFromToken(authHeader, ownerProjectLinkId);
        Comments comment = commentsService.addComment(postId, content, user);
        return new CommentDto(comment, user.getId());
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @GetMapping("/{postId}")
    public ResponseEntity<List<CommentDto>> getComments(@PathVariable Long postId,
                                                        @RequestParam Long ownerProjectLinkId,
                                                        @RequestHeader("Authorization") String authHeader) {
        Users user = getUserFromToken(authHeader, ownerProjectLinkId);
        List<Comments> comments = commentsService.getCommentsByPost(postId);
        List<CommentDto> dtos = comments.stream()
                .map(c -> new CommentDto(c, user.getId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "401")
    })
    @DeleteMapping("/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable Long commentId,
                                                @RequestParam Long ownerProjectLinkId,
                                                @RequestHeader("Authorization") String authHeader) {
        Users user = getUserFromToken(authHeader, ownerProjectLinkId);
        commentsService.deleteComment(commentId, user);
        return ResponseEntity.ok("Comment deleted successfully");
    }
}
