package com.build4all.publish.repository;



import com.build4all.publish.domain.PublishStore;
import com.build4all.publish.domain.StorePublisherProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorePublisherProfileRepository extends JpaRepository<StorePublisherProfile, Long> {
    Optional<StorePublisherProfile> findByStore(PublishStore store);
}
