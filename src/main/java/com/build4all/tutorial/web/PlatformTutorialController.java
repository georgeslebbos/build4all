package com.build4all.tutorial.web;

import com.build4all.tutorial.service.PlatformTutorialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tutorial")
public class PlatformTutorialController {

    private final PlatformTutorialService service;

    public PlatformTutorialController(PlatformTutorialService service) {
        this.service = service;
    }

    @GetMapping("/owner-guide")
    public ResponseEntity<?> getOwnerGuide() {
        var t = service.getOwnerGuide();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", PlatformTutorialService.OWNER_APP_GUIDE);
        data.put("videoUrl", (t == null) ? null : t.getVideoUrl()); // ✅ null ok

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "OK");
        res.put("data", data);

        return ResponseEntity.ok(res);
    }
}