package com.build4all.homebanner.repository;

import com.build4all.homebanner.domain.HomeBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HomeBannerRepository extends JpaRepository<HomeBanner, Long> {

    @Query("""
           select b
           from HomeBanner b
           where b.ownerProject.id = :ownerProjectId
             and b.active = true
             and (b.startAt is null or b.startAt <= :now)
             and (b.endAt   is null or b.endAt   >= :now)
           order by b.sortOrder asc, b.createdAt desc
           """)
    List<HomeBanner> findActiveBannersForOwnerProject(@Param("ownerProjectId") Long ownerProjectId,
                                                      @Param("now") LocalDateTime now);

    List<HomeBanner> findByOwnerProject_IdOrderBySortOrderAscCreatedAtDesc(Long ownerProjectId);
}