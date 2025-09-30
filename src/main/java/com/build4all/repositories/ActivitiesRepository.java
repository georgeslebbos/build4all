package com.build4all.repositories;

import com.build4all.dto.AdminActivityDTO;
import com.build4all.entities.Activity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivitiesRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByBusinessId(Long businessId);

    List<Activity> findByItemTypeId(Long itemTypeId);

    @Query("""
            SELECT a
            FROM Activity a
            WHERE a.itemType.id IN (
               SELECT ui.interest.id
               FROM UserInterests ui
               WHERE ui.id.user.id = :userId
            )
            AND a.endDatetime > :now
            """)
    List<Activity> findUpcomingByUserInterests(@Param("userId") Long userId,
                                               @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"business"})
    @Query("""
           SELECT a FROM Activity a
           JOIN a.business b
           WHERE b.status.name = 'ACTIVE' AND b.isPublicProfile = true
           """)
    List<Activity> findAllPublicActive();

    @Query("""
    	       SELECT a
    	       FROM Activity a
    	       JOIN a.business b
    	       WHERE b.status.name = 'ACTIVE'
    	         AND b.isPublicProfile = true
    	       """)
    	List<Activity> findAllActivitiesWithBusinessInfo();
}
