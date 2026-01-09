package com.build4all.publish.service;

import com.build4all.publish.domain.PublishStore;
import com.build4all.publish.domain.StorePublisherProfile;
import com.build4all.publish.dto.UpsertPublisherProfileDto;
import com.build4all.publish.repository.StorePublisherProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StorePublisherProfileService {

    private final StorePublisherProfileRepository repo;

    public StorePublisherProfileService(StorePublisherProfileRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<StorePublisherProfile> listAll() {
        return repo.findAll();
    }

    @Transactional
    public StorePublisherProfile upsert(UpsertPublisherProfileDto dto) {

        StorePublisherProfile profile = repo.findByStore(dto.getStore())
                .orElseGet(() -> {
                    StorePublisherProfile p = new StorePublisherProfile();
                    p.setStore(dto.getStore());
                    return p;
                });

        profile.setDeveloperName(dto.getDeveloperName().trim());
        profile.setDeveloperEmail(dto.getDeveloperEmail().trim());
        profile.setPrivacyPolicyUrl(dto.getPrivacyPolicyUrl().trim());

        return repo.save(profile);
    }

    /**
     * ✅ Creates missing profiles with defaults (because columns are NOT NULL).
     */
    @Transactional
    public void seedDefaultsIfMissing(String developerName, String developerEmail, String privacyPolicyUrl) {

        for (PublishStore store : PublishStore.values()) {
            repo.findByStore(store).orElseGet(() -> {
                StorePublisherProfile p = new StorePublisherProfile();
                p.setStore(store);

                p.setDeveloperName(developerName);
                p.setDeveloperEmail(developerEmail);
                p.setPrivacyPolicyUrl(privacyPolicyUrl);

                return repo.save(p);
            });
        }
    }
}
