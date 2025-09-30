package com.build4all.services;

import com.build4all.entities.Comments;
import com.build4all.entities.Posts;
import com.build4all.entities.Users;
import com.build4all.repositories.CommentsRepository;
import com.build4all.repositories.PostsRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentsService {

    private final CommentsRepository commentsRepo;
    private final PostsRepository postsRepo;
    private final NotificationsService notificationsService;

    public CommentsService(CommentsRepository commentsRepo,
                           PostsRepository postsRepo,
                           NotificationsService notificationsService) {
        this.commentsRepo = commentsRepo;
        this.postsRepo = postsRepo;
        this.notificationsService = notificationsService;
    }

    public Comments addComment(Long postId, String content, Users user) {
        Posts post = postsRepo.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        Comments comment = new Comments(post, user, content);
        Comments savedComment = commentsRepo.save(comment);

        // 🔔 Add notification for the post owner
        if (!user.getId().equals(post.getUser().getId())) {
            notificationsService.createNotification(
                    post.getUser(),
                    user.getUsername() + " commented on your post",
                    "ACTIVITY_UPDATE"
            );
        }

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

        // Only allow the author to delete their comment
        if (!comment.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not authorized to delete this comment");
        }

        commentsRepo.delete(comment);
    }
}
