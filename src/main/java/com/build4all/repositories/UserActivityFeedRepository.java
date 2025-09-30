package com.build4all.repositories;

import com.build4all.entities.UserActivityFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserActivityFeedRepository extends JpaRepository<UserActivityFeed, Long> {
   
}
