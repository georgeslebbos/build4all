package com.build4all.licensing.service;

import com.build4all.licensing.domain.PlanCatalog;
import com.build4all.licensing.domain.PlanCode;
import com.build4all.licensing.repository.PlanCatalogRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class PlanCatalogSeeder implements ApplicationRunner {

    private final PlanCatalogRepository planRepo;

    public PlanCatalogSeeder(PlanCatalogRepository planRepo) {
        this.planRepo = planRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsert(PlanCode.FREE, "Free", 20, false, 12);
        upsert(PlanCode.PRO_HOSTEDB, "Pro Hosted DB (Build4all)", null, false, 12);
        upsert(PlanCode.DEDICATED, "Dedicated Server", null, true, 12);
    }

    private void upsert(PlanCode code,
                        String displayName,
                        Integer usersAllowed,
                        boolean requiresDedicatedServer,
                        int billingCycleMonths) {

        planRepo.findById(code).orElseGet(() -> {
            PlanCatalog p = new PlanCatalog();
            p.setCode(code);
            p.setDisplayName(displayName);
            p.setUsersAllowed(usersAllowed); // null = unlimited
            p.setRequiresDedicatedServer(requiresDedicatedServer);
            p.setBillingCycleMonths(billingCycleMonths);
            return planRepo.save(p);
        });
    }
}
