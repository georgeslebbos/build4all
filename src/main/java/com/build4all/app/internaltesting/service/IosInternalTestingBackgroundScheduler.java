package com.build4all.app.internaltesting.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "build4all.ios-internal",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class IosInternalTestingBackgroundScheduler {

    private final IosInternalTestingRequestService iosInternalTestingRequestService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public IosInternalTestingBackgroundScheduler(
            IosInternalTestingRequestService iosInternalTestingRequestService
    ) {
        this.iosInternalTestingRequestService = iosInternalTestingRequestService;
    }

    @Scheduled(fixedDelayString = "${build4all.ios-internal.sync-fixed-delay-ms:60000}")
    public void runBackgroundSync() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            int updated = iosInternalTestingRequestService.runBackgroundSyncCycle();
            System.out.println("✅ IosInternalTestingBackgroundScheduler updated count => " + updated);
        } catch (Exception ex) {
            System.out.println("❌ IosInternalTestingBackgroundScheduler failed => "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            ex.printStackTrace();
        } finally {
            running.set(false);
        }
    }
}