package com.build4all.social.service;

import com.build4all.webSocket.service.WebSocketEventService;
import com.build4all.social.dto.PostDto;
import com.build4all.social.domain.PostVisibility;
import com.build4all.social.domain.Posts;
import com.build4all.user.domain.Users;
import com.build4all.social.repository.PostVisibilityRepository;
import com.build4all.social.repository.PostsRepository;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Post business logic. Supports optional owner scoping via aupId.
 */
@Service
public class PostService {

    private final PostsRepository postsRepository;
    private final PostVisibilityRepository postVisibilityRepository;
    private final FriendshipService friendshipService;
    private final WebSocketEventService ws;
    private final AdminUserProjectRepository aupRepo;

    @Autowired
    public PostService(PostsRepository postsRepository,
                       PostVisibilityRepository postVisibilityRepository,
                       FriendshipService friendshipService,
                       WebSocketEventService ws,
                       AdminUserProjectRepository aupRepo) {
        this.postsRepository = postsRepository;
        this.postVisibilityRepository = postVisibilityRepository;
        this.friendshipService = friendshipService;
        this.ws = ws;
        this.aupRepo = aupRepo;
    }

    /**
     * Create a post. aupId is optional; if provided, the post is scoped to that owner-project link.
     */
    public Posts createPost(String content,
                            MultipartFile image,
                            String hashtags,
                            Users user,
                            PostVisibility visibility,
                            Long aupId) {
        String imageUrl = null;

        if (image != null && !image.isEmpty()) {
            try {
                String filename = System.currentTimeMillis() + "_" + image.getOriginalFilename();
                Path uploadPath = Paths.get("uploads/");
                if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
                Path filePath = uploadPath.resolve(filename);
                Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                imageUrl = "/uploads/" + filename;
            } catch (Exception e) {
                throw new RuntimeException("Error uploading image", e);
            }
        }

        Posts post = new Posts(user, content, imageUrl, hashtags);
        post.setVisibility(visibility);

        // Optional owner scoping
        if (aupId != null) {
            aupRepo.findById(aupId).ifPresent(post::setOwnerProject);
        }

        Posts savedPost = postsRepository.save(post);

        // Legacy and JSON updates via websocket
        ws.sendPostCreated(savedPost.getId().toString());
        Map<String, Object> changes = new HashMap<>();
        if (savedPost.getContent() != null)  changes.put("content", savedPost.getContent());
        if (savedPost.getImageUrl() != null) changes.put("imageUrl", savedPost.getImageUrl());
        if (savedPost.getHashtags() != null) changes.put("hashtags", savedPost.getHashtags());
        String visName = (savedPost.getVisibility() != null && savedPost.getVisibility().getName() != null)
                ? savedPost.getVisibility().getName() : "PUBLIC";
        changes.put("visibility", visName);
        ws.sendPostUpdated(savedPost.getId(), changes);

        return savedPost;
    }

    @Transactional
    public boolean deletePostByUser(Long postId, Long userId) {
        Optional<Posts> post = postsRepository.findById(postId);
        if (post.isPresent() && post.get().getUser().getId().equals(userId)) {
            postsRepository.delete(post.get());
            ws.sendPostDeleted(String.valueOf(postId));
            ws.sendPostUpdated(postId, Collections.singletonMap("deleted", true));
            return true;
        }
        return false;
    }

    public void deletePost(Long postId, Users user) {
        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!post.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to delete this post");
        }
        postsRepository.delete(post);
        ws.sendPostDeleted(String.valueOf(postId));
        ws.sendPostUpdated(postId, Collections.singletonMap("deleted", true));
    }

    @Transactional
    public List<PostDto> getAllPostDtos(Long currentUserId) {
        List<Posts> posts = postsRepository.findAll();
        return posts.stream()
                .filter(post -> {
                    try {
                        Users poster = post.getUser();
                        if (poster.getStatus() == null || !"ACTIVE".equals(poster.getStatus().getName()))
                            return false;

                        if (Objects.equals(poster.getId(), currentUserId))
                            return true;

                        String visibilityName = post.getVisibility() != null ? post.getVisibility().getName() : "PUBLIC";
                        if ("PUBLIC".equalsIgnoreCase(visibilityName)) return true;

                        if ("FRIENDS_ONLY".equalsIgnoreCase(visibilityName) &&
                                friendshipService.areFriends(
                                        new Users() {{ setId(currentUserId); }},
                                        new Users() {{ setId(poster.getId()); }}
                                )) {
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(post -> new PostDto(post, currentUserId))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PostDto> getPostDtosByUser(Long userId) {
        return postsRepository.findByUserId(userId)
                .stream()
                .map(post -> new PostDto(post, userId))
                .collect(Collectors.toList());
    }

    public Posts updatePost(Long postId,
                            Users user,
                            String newContent,
                            String newHashtags,
                            PostVisibility newVisibility,
                            MultipartFile newImage) {
        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!post.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Not authorized");

        if (newContent != null)   post.setContent(newContent);
        if (newHashtags != null)  post.setHashtags(newHashtags);
        if (newVisibility != null) post.setVisibility(newVisibility);

        if (newImage != null && !newImage.isEmpty()) {
            try {
                String filename = System.currentTimeMillis() + "_" + newImage.getOriginalFilename();
                Path uploadPath = Paths.get("uploads/");
                if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
                Path filePath = uploadPath.resolve(filename);
                Files.copy(newImage.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                post.setImageUrl("/uploads/" + filename);
            } catch (Exception e) {
                throw new RuntimeException("Error uploading image", e);
            }
        }

        Posts saved = postsRepository.save(post);

        Map<String, Object> changes = new HashMap<>();
        if (saved.getContent() != null)  changes.put("content", saved.getContent());
        if (saved.getImageUrl() != null) changes.put("imageUrl", saved.getImageUrl());
        if (saved.getHashtags() != null) changes.put("hashtags", saved.getHashtags());
        String visName = (saved.getVisibility() != null && saved.getVisibility().getName() != null)
                ? saved.getVisibility().getName() : "PUBLIC";
        changes.put("visibility", visName);
        ws.sendPostUpdated(saved.getId(), changes);

        return saved;
    }

    /** Owner-scoped feed (optional). */
    @Transactional(readOnly = true)
    public List<PostDto> getOwnerFeed(Long aupId, Long currentUserId) {
        return postsRepository.findByOwnerProject_IdOrderByPostDatetimeDesc(aupId)
                .stream()
                .map(p -> new PostDto(p, currentUserId))
                .collect(Collectors.toList());
    }
}
