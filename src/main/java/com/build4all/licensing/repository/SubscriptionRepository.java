package com.build4all.licensing.repository;

import com.build4all.licensing.domain.Subscription;
import com.build4all.licensing.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findTopByApp_IdAndStatusOrderByPeriodEndDesc(Long aupId, SubscriptionStatus status);

    //  any subscription exists for this app
    boolean existsByApp_Id(Long aupId);

    //  last subscription (active/expired/anything) - useful for debugging
    Optional<Subscription> findTopByApp_IdOrderByPeriodEndDesc(Long aupId);
    
    @Modifying
    @Query("update Subscription s set s.status = :newStatus where s.app.id = :aupId and s.status = :oldStatus")
    int bulkUpdateStatus(@Param("aupId") Long aupId,
                         @Param("oldStatus") SubscriptionStatus oldStatus,
                         @Param("newStatus") SubscriptionStatus newStatus);

}
