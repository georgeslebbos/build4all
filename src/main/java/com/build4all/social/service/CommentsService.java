package com.build4all.social.service;

import com.build4all.notifications.service.NotificationsService;
import com.build4all.webSocket.service.WebSocketEventService;
import com.build4all.social.domain.Comments;
import com.build4all.social.domain.Posts;
import com.build4all.user.domain.Users;
import com.build4all.social.repository.CommentsRepository;
import com.build4all.social.repository.PostsRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentsService {

    private final CommentsRepository commentsRepo;
    private final PostsRepository postsRepo;
    private final NotificationsService notificationsService;
    private final WebSocketEventService ws;

    public CommentsService(CommentsRepository commentsRepo,
                           PostsRepository postsRepo,
                           NotificationsService notificationsService,
                           WebSocketEventService ws) {
        this.commentsRepo = commentsRepo;
        this.postsRepo = postsRepo;
        this.notificationsService = notificationsService;
        this.ws = ws;
    }

    public Comments addComment(Long postId, String content, Users user) {
        Posts post = postsRepo.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Comments savedComment = commentsRepo.save(new Comments(post, user, content));

        if (!user.getId().equals(post.getUser().getId())) {
            notificationsService.createNotification(
                    post.getUser(),
                    (user.getUsername() == null ? "Someone" : user.getUsername()) + " commented on your post",
                    "ACTIVITY_UPDATE"
            );
            ws.sendUnreadBumped(post.getUser().getId());
        }

        Map<String, Object> dto = new HashMap<>();
        dto.put("id", savedComment.getId());
        if (savedComment.getUser() != null) {
            if (savedComment.getUser().getFirstName() != null)
                dto.put("firstName", savedComment.getUser().getFirstName());
            if (savedComment.getUser().getLastName() != null)
                dto.put("lastName", savedComment.getUser().getLastName());
            if (savedComment.getUser().getProfilePictureUrl() != null)
                dto.put("profilePictureUrl", savedComment.getUser().getProfilePictureUrl());
        }
        if (savedComment.getContent() != null)
            dto.put("content", savedComment.getContent());
        dto.put("isMine", false);

        ws.sendCommentAdded(post.getId(), dto);
        return savedComment;
    }

    public List<Comments> getCommentsByPost(Long postId) {
        Posts post = postsRepo.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        return commentsRepo.findByPost(post);
    }

    public void deleteComment(Long commentId, Users user) {
        Comments comment = commentsRepo.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not authorized to delete this comment");
        }

        commentsRepo.delete(comment);
        ws.sendCommentDeleted(comment.getPost().getId(), comment.getId());
    }
}
