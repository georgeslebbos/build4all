package com.build4all.social.repository;

import com.build4all.social.domain.PostVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostVisibilityRepository extends JpaRepository<PostVisibility, Long> {
    Optional<PostVisibility> findByName(String name);
    Optional<PostVisibility>  findByNameIgnoreCase(String name);
}
