package com.build4all.repositories;

import com.build4all.entities.Posts;
import com.build4all.entities.PostVisibility;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostsRepository extends JpaRepository<Posts, Long> {

    @EntityGraph(attributePaths = {"likedUsers", "user"})
    List<Posts> findAll();

    List<Posts> findByUserId(Long userId);

    // ✅ Use PostVisibility entity instead of enum
    List<Posts> findByVisibility(PostVisibility visibility);

    List<Posts> findByUserIdAndVisibilityIn(Long userId, List<PostVisibility> visibilities);
}
