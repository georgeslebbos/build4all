package com.build4all.social.repository;

import com.build4all.social.domain.PostLikes;
import com.build4all.user.domain.Users;
import com.build4all.social.domain.Posts;
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
