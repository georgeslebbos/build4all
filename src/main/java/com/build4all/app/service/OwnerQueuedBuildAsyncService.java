package com.build4all.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OwnerQueuedBuildAsyncService {

    private static final Logger log = LoggerFactory.getLogger(OwnerQueuedBuildAsyncService.class);

    private final AppRequestService appRequestService;

    public OwnerQueuedBuildAsyncService(AppRequestService appRequestService) {
        this.appRequestService = appRequestService;
    }

    @Async("buildExecutor")
    public void dispatchBoth(
            Long linkId,
            String apiBaseUrlOverride,
            String navJson,
            String homeJson,
            String enabledFeaturesJson,
            String brandingJson,
            String ownerEmail,
            String ownerName
    ) {
        try {
            appRequestService.rebuildAndroidAndIos(
                    linkId,
                    false, // do not bump again here
                    false, // do not bump again here
                    apiBaseUrlOverride,
                    navJson,
                    homeJson,
                    enabledFeaturesJson,
                    brandingJson,
                    ownerEmail,
                    ownerName
            );
        } catch (Exception ex) {
            log.error("Async build dispatch failed for linkId={}", linkId, ex);
        }
    }
}