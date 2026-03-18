package com.build4all.app.internaltesting.scheduler;

import com.build4all.app.internaltesting.service.IosInternalTestingRequestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class IosInternalTestingSyncScheduler {

    private final IosInternalTestingRequestService requestService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${build4all.ios-internal.sync.enabled:true}")
    private boolean enabled;

    public IosInternalTestingSyncScheduler(IosInternalTestingRequestService requestService) {
        this.requestService = requestService;
    }

    @Scheduled(
            initialDelayString = "${build4all.ios-internal.sync.initial-delay-ms:15000}",
            fixedDelayString = "${build4all.ios-internal.sync.fixed-delay-ms:60000}"
    )
    public void syncWaitingInternalTestingRequests() {
        System.out.println("⏰ iOS internal testing scheduler started");

        if (!enabled) {
            System.out.println("⏸️ iOS internal testing scheduler disabled");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            System.out.println("⏭️ iOS internal testing sync skipped because previous run is still in progress");
            return;
        }

        try {
            int updated = requestService.syncWaitingRequests();
            System.out.println("✅ iOS internal testing scheduler finished. Updated count = " + updated);
        } catch (Exception ex) {
            System.out.println("❌ iOS internal testing scheduler failed: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            running.set(false);
        }
    }
    
}