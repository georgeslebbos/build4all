package com.build4all.repositories;

import com.build4all.entities.Comments;
import com.build4all.entities.Posts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentsRepository extends JpaRepository<Comments, Long> {
    
    
    List<Comments> findByPost(Posts post);
}
