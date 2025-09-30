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

    public PostLikesService(PostLikesRepository likesRepository,
                            PostsRepository postsRepository,
                            NotificationsService notificationsService) {
        this.likesRepository = likesRepository;
        this.postsRepository = postsRepository;
        this.notificationsService = notificationsService;
    }

    public String toggleLike(Long postId, Users user) {
        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post non trouvé"));

        return likesRepository.findByUserAndPost(user, post)
                .map(like -> {
                    likesRepository.delete(like);
                    return "disliked";
                })
                .orElseGet(() -> {
                    likesRepository.save(new PostLikes(user, post));

                    // Notify post owner (if not self)
                    if (!user.getId().equals(post.getUser().getId())) {
                        notificationsService.createNotification(
                                post.getUser(),
                                user.getUsername() + " liked your post",
                                "ACTIVITY_UPDATE" // <-- using code string instead of enum
                        );
                    }

                    return "Liked";
                });
    }

    public long countLikes(Long postId) {
        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("not found"));
        return likesRepository.countByPost(post);
    }
}
