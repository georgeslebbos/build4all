package com.build4all.features.activity.repository;

import com.build4all.features.activity.domain.Activity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivitiesRepository extends JpaRepository<Activity, Long> {

    // --- ORIGINAL SIMPLE METHODS (kept if used elsewhere) ---
    List<Activity> findByBusinessId(Long businessId);
    List<Activity> findByItemTypeId(Long itemTypeId);

    // --- FETCH-JOINED READS USED BY SERVICE ---

    @Query("""
        select a
        from Activity a
        join fetch a.itemType it
        join fetch a.business b
        left join fetch b.status
    """)
    List<Activity> findAllWithItemTypeAndBusiness();

    @Query("""
        select a
        from Activity a
        join fetch a.itemType it
        join fetch a.business b
        left join fetch b.status
        where it.id = :typeId
    """)
    List<Activity> findByItemTypeIdWithJoins(@Param("typeId") Long typeId);

    @Query("""
        select a
        from Activity a
        join fetch a.itemType it
        join fetch a.business b
        left join fetch b.status
        where b.id = :businessId
    """)
    List<Activity> findByBusinessIdWithJoins(@Param("businessId") Long businessId);

    @Query("""
        select a
        from Activity a
        join fetch a.itemType it
        join fetch a.business b
        left join fetch b.status
        where a.id = :id
    """)
    Optional<Activity> findByIdWithJoins(@Param("id") Long id);

    // Category-based (used by /category-based), eager for DTO needs
    @Query("""
        select a
        from Activity a
        join fetch a.itemType it
        join fetch a.business b
        left join fetch b.status
        where a.itemType.id in (
           select ui.category.id
           from UserCategories ui
           where ui.id.user.id = :userId
        )
        and a.endDatetime > :now
    """)
    List<Activity> findUpcomingByUserCategoriesWithJoins(@Param("userId") Long userId,
                                                         @Param("now") LocalDateTime now);

    // If you need this listing, ensure related fields are loaded
    @EntityGraph(attributePaths = {"business", "business.status", "itemType"})
    @Query("""
       SELECT a FROM Activity a
       JOIN a.business b
       WHERE b.status.name = 'ACTIVE' AND b.isPublicProfile = true
    """)
    List<Activity> findAllPublicActive();

    @EntityGraph(attributePaths = {"business", "business.status", "itemType"})
    @Query("""
       SELECT a
       FROM Activity a
       JOIN a.business b
       WHERE b.status.name = 'ACTIVE'
         AND b.isPublicProfile = true
    """)
    List<Activity> findAllActivitiesWithBusinessInfo();
    @Query("""
    	    select a
    	    from Activity a
    	    join fetch a.itemType it
    	    join fetch a.business b
    	    left join fetch b.status
    	    where a.ownerProject.id = :aupId
    	""")
    	List<Activity> findByOwnerProject_Id(@Param("aupId") Long aupId);


    List<Activity> findByOwnerProject_IdAndItemType_Id(Long aupId, Long typeId);

    @Query("select a from Activity a join fetch a.itemType it join fetch a.business b where a.ownerProject.id = :aupId")
    List<Activity> findAllByAupWithJoins(Long aupId);
}
