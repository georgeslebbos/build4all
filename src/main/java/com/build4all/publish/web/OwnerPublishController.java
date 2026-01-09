package com.build4all.publish.web;

import com.build4all.publish.dto.CreatePublishDraftDto;
import com.build4all.publish.dto.PublishDraftUpdateDto;
import com.build4all.publish.service.AppPublishService;
import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/owner/publish")
public class OwnerPublishController {

    private final AppPublishService publishService;
    private final JwtUtil jwtUtil;

    public OwnerPublishController(AppPublishService publishService, JwtUtil jwtUtil) {
        this.publishService = publishService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/draft")
    public ResponseEntity<?> getOrCreateDraft(
            HttpServletRequest request,
            @Valid @RequestBody CreatePublishDraftDto dto
    ) {
        String token = jwtUtil.extractTokenFromRequest(request);

        if (!jwtUtil.isOwnerToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only OWNER can request publish"));
        }

        Long ownerAdminId = jwtUtil.extractId(token);

        var draft = publishService.getOrCreateDraft(dto.getAupId(), dto.getPlatform(), dto.getStore(), ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Draft ready",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(draft)
        ));
    }

    @PatchMapping("/{requestId}")
    public ResponseEntity<?> patchDraft(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @RequestBody PublishDraftUpdateDto dto
    ) {
        String token = jwtUtil.extractTokenFromRequest(request);

        if (!jwtUtil.isOwnerToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only OWNER can update publish draft"));
        }

        Long ownerAdminId = jwtUtil.extractId(token);

        var updated = publishService.patchDraft(requestId, dto, ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Draft updated",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(updated)
        ));
    }

    // ✅ NEW: Upload icon + screenshots as FILES (multipart)
    @PostMapping(value = "/{requestId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAssets(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @RequestPart(value = "appIcon", required = false) MultipartFile appIcon,
            @RequestPart(value = "screenshots", required = false) MultipartFile[] screenshots
    ) {
        String token = jwtUtil.extractTokenFromRequest(request);

        if (!jwtUtil.isOwnerToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only OWNER can upload publish assets"));
        }

        Long ownerAdminId = jwtUtil.extractId(token);

        var updated = publishService.uploadAssets(requestId, appIcon, screenshots, ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Assets uploaded",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(updated)
        ));
    }

    @PostMapping("/{requestId}/submit")
    public ResponseEntity<?> submit(
            HttpServletRequest request,
            @PathVariable Long requestId
    ) {
        String token = jwtUtil.extractTokenFromRequest(request);

        if (!jwtUtil.isOwnerToken(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only OWNER can submit publish request"));
        }

        Long ownerAdminId = jwtUtil.extractId(token);

        var submitted = publishService.submitForReview(requestId, ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Submitted for review",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(submitted)
        ));
    }
}
