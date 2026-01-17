package com.build4all.ai.service;

import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiEntitlementService {

    private final AdminUserProjectRepository aupRepo;

    public AiEntitlementService(AdminUserProjectRepository aupRepo) {
        this.aupRepo = aupRepo;
    }

    public void assertAiEnabled(Long linkId) {
        boolean enabled = aupRepo.isOwnerAiEnabledByLinkId(linkId).orElse(false);
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI disabled for this owner");
        }
    }
}
