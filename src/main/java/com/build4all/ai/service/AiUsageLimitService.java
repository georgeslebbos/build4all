package com.build4all.ai.service;

import com.build4all.ai.domain.OwnerAiUsage;
import com.build4all.ai.repository.OwnerAiUsageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
public class AiUsageLimitService {

    // ✅ Change this number anytime (or later make it per plan)
    private static final int DAILY_LIMIT = 20;

    private final OwnerAiUsageRepository repo;

    public AiUsageLimitService(OwnerAiUsageRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void checkAndIncrement(Long ownerId) {
        LocalDate today = LocalDate.now();

        OwnerAiUsage usage = repo.findByOwnerIdAndUsageDate(ownerId, today)
                .orElseGet(() -> new OwnerAiUsage(ownerId, today, 0));

        if (usage.getRequestCount() >= DAILY_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Daily AI limit reached"
            );
        }

        usage.setRequestCount(usage.getRequestCount() + 1);
        repo.save(usage);
    }

    public int getDailyLimit() {
        return DAILY_LIMIT;
    }
}
