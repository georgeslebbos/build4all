package com.build4all.social.dto;

import com.build4all.social.domain.Posts;
import com.build4all.user.domain.Users;

/**
 * DTO for Posts to avoid exposing deep entity graphs to clients.
 */
public class PostDto {
    public Long id;
    public String content;
    public String hashtags;
    public String imageUrl;
    public boolean isLiked;
    public int likeCount;
    public int commentCount;
    public String postDatetime;
    public Long userId;
    public String firstName;
    public String lastName;
    public String profilePictureUrl;
    public String visibility; // e.g., "PUBLIC" or "FRIENDS_ONLY"

    public PostDto(Posts post, Long currentUserId) {
        this.id = post.getId();
        this.content = post.getContent();
        this.hashtags = post.getHashtags();
        this.imageUrl = post.getImageUrl();
        this.postDatetime = post.getPostDatetime() != null ? post.getPostDatetime().toString() : null;

        this.likeCount = post.getLikedUsers() != null ? post.getLikedUsers().size() : 0;
        this.commentCount = post.getComments() != null ? post.getComments().size() : 0;

        this.isLiked = post.getLikedUsers() != null &&
                post.getLikedUsers().stream().anyMatch(u -> u.getId().equals(currentUserId));

        this.visibility = (post.getVisibility() != null && post.getVisibility().getName() != null)
                ? post.getVisibility().getName()
                : "PUBLIC";

        Users user = post.getUser();
        if (user != null) {
            this.userId = user.getId();
            this.firstName = user.getFirstName();
            this.lastName = user.getLastName();
            this.profilePictureUrl = user.getProfilePictureUrl();
        }
    }
}
