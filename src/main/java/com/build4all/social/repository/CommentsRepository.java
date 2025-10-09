package com.build4all.social.repository;

import com.build4all.social.domain.Comments;
import com.build4all.social.domain.Posts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentsRepository extends JpaRepository<Comments, Long> {
    
    
    List<Comments> findByPost(Posts post);
}
