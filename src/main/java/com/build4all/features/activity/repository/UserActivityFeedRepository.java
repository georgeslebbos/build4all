package com.build4all.features.activity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.build4all.features.activity.domain.UserActivityFeed;

@Repository
public interface UserActivityFeedRepository extends JpaRepository<UserActivityFeed, Long> {
   
}
