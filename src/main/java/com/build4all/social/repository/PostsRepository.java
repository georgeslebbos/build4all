package com.build4all.social.repository;

import com.build4all.social.domain.Posts;
import com.build4all.social.domain.PostVisibility;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PostsRepository extends JpaRepository<Posts, Long> {

    @EntityGraph(attributePaths = {"likedUsers", "user"})
    List<Posts> findAll();

    List<Posts> findByUserId(Long userId);

    List<Posts> findByVisibility(PostVisibility visibility);

    List<Posts> findByUserIdAndVisibilityIn(Long userId, List<PostVisibility> visibilities);

    /** Owner-scoped feed (optional) */
    List<Posts> findByOwnerProject_IdOrderByPostDatetimeDesc(Long aupId);
}
