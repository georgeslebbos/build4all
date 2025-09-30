package com.build4all.repositories;

import com.build4all.entities.PostLikes;
import com.build4all.entities.Users;
import com.build4all.entities.Posts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostLikesRepository extends JpaRepository<PostLikes, Long> {
  Optional<PostLikes> findByUserAndPost(Users user, Posts post);

  long countByPost(Posts post);

  int countByPostId(Long postId);

  boolean existsByPostIdAndUser(Long postId, Users user);

}
