package com.build4all.repositories.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.build4all.entities.activity.UserActivityFeed;

@Repository
public interface UserActivityFeedRepository extends JpaRepository<UserActivityFeed, Long> {
   
}
