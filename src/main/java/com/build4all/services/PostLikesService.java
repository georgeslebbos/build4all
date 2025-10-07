package com.build4all.services;

import com.build4all.entities.PostLikes;
import com.build4all.entities.Posts;
import com.build4all.entities.Users;
import com.build4all.repositories.PostLikesRepository;
import com.build4all.repositories.PostsRepository;
import org.springframework.stereotype.Service;

@Service
public class PostLikesService {

    private final PostLikesRepository likesRepository;
    private final PostsRepository postsRepository;
    private final NotificationsService notificationsService;
    private final WebSocketEventService ws;

    public PostLikesService(PostLikesRepository likesRepository,
                            PostsRepository postsRepository,
                            NotificationsService notificationsService,
                            WebSocketEventService ws) {
        this.likesRepository = likesRepository;
        this.postsRepository = postsRepository;
        this.notificationsService = notificationsService;
        this.ws = ws;
    }

    public String toggleLike(Long postId, Users user) {
        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post non trouvé"));

        return likesRepository.findByUserAndPost(user, post)
                .map(like -> {
                    likesRepository.delete(like);
                    // broadcast unliked
                    ws.sendLikeChanged(postId, false, user.getId());
                    return "disliked";
                })
                .orElseGet(() -> {
                    likesRepository.save(new PostLikes(user, post));

                    if (!user.getId().equals(post.getUser().getId())) {
                        notificationsService.createNotification(
                                post.getUser(),
                                user.getUsername() + " liked your post",
                                "ACTIVITY_UPDATE"
                        );
                        ws.sendUnreadBumped(post.getUser().getId());
                    }

                    // broadcast liked
                    ws.sendLikeChanged(postId, true, user.getId());
                    return "Liked";
                });
    }

    public long countLikes(Long postId) {
        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("not found"));
        return likesRepository.countByPost(post);
    }
}
