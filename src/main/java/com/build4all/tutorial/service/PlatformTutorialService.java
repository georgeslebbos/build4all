package com.build4all.tutorial.service;

import com.build4all.tutorial.domain.PlatformTutorial;
import com.build4all.tutorial.repository.PlatformTutorialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTutorialService {

    public static final String OWNER_APP_GUIDE = "OWNER_APP_GUIDE";

    private final PlatformTutorialRepository repo;

    public PlatformTutorialService(PlatformTutorialRepository repo) {
        this.repo = repo;
    }

    public PlatformTutorial getOwnerGuide() {
        return repo.findById(OWNER_APP_GUIDE).orElse(null);
    }

    @Transactional
    public PlatformTutorial upsertOwnerGuide(String videoUrl) {
        PlatformTutorial t = repo.findById(OWNER_APP_GUIDE).orElseGet(() -> {
            PlatformTutorial x = new PlatformTutorial();
            x.setCode(OWNER_APP_GUIDE);
            return x;
        });

        String url = (videoUrl == null) ? null : videoUrl.trim();
        if (url != null && url.isEmpty()) url = null;

     // داخل upsertOwnerGuide بعد trim
        if (url != null) {
            boolean ok =
                url.startsWith("http://") ||
                url.startsWith("https://") ||
                url.startsWith("/uploads/");   // ✅ path allowed (strict)
            if (!ok) {
                throw new IllegalArgumentException("videoUrl must be http/https or /uploads/...");
            }
        }

        t.setVideoUrl(url);
        return repo.save(t);
    }
}