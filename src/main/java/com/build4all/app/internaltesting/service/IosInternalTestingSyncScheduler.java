package com.build4all.app.internaltesting.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IosInternalTestingSyncScheduler {

    private final IosInternalTestingRequestService requestService;

    public IosInternalTestingSyncScheduler(IosInternalTestingRequestService requestService) {
        this.requestService = requestService;
    }

    @Scheduled(fixedDelayString = "${build4all.ios-internal.sync-delay-ms:300000}")
    public void syncWaitingRequests() {
        requestService.syncWaitingRequests();
    }
}