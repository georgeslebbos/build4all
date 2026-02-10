package com.build4all.licensing.repository;

import com.build4all.licensing.domain.Subscription;
import com.build4all.licensing.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findTopByApp_IdAndStatusOrderByPeriodEndDesc(Long aupId, SubscriptionStatus status);

    //  any subscription exists for this app
    boolean existsByApp_Id(Long aupId);

    //  last subscription (active/expired/anything) - useful for debugging
    Optional<Subscription> findTopByApp_IdOrderByPeriodEndDesc(Long aupId);
}
