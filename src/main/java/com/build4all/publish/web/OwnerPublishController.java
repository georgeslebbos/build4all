package com.build4all.publish.web;

import com.build4all.publish.dto.CreatePublishDraftDto;
import com.build4all.publish.dto.PublishDraftUpdateDto;
import com.build4all.publish.service.AppPublishService;
import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private String token(HttpServletRequest request) {
        return jwtUtil.extractTokenFromRequest(request);
    }

    @PostMapping("/draft")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> getOrCreateDraft(
            HttpServletRequest request,
            @Valid @RequestBody CreatePublishDraftDto dto
    ) {
        String t = token(request);
        Long ownerAdminId = jwtUtil.extractAdminId(t);

        // strict tenant lock: owner cannot open/create draft for another owner's app
        jwtUtil.requireTenantMatch(t, dto.getAupId());

        var draft = publishService.getOrCreateDraft(
                dto.getAupId(),
                dto.getPlatform(),
                dto.getStore(),
                ownerAdminId
        );

        return ResponseEntity.ok(Map.of(
                "message", "Draft ready",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(draft)
        ));
    }

    @PatchMapping("/{requestId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> patchDraft(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @RequestBody PublishDraftUpdateDto dto
    ) {
        String t = token(request);
        Long ownerAdminId = jwtUtil.extractAdminId(t);

        var updated = publishService.patchDraft(requestId, dto, ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Draft updated",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(updated)
        ));
    }

    @PostMapping(value = "/{requestId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> uploadAssets(
            HttpServletRequest request,
            @PathVariable Long requestId,
            @RequestPart(value = "appIcon", required = false) MultipartFile appIcon,
            @RequestPart(value = "screenshots", required = false) MultipartFile[] screenshots
    ) {
        String t = token(request);
        Long ownerAdminId = jwtUtil.extractAdminId(t);

        if (screenshots == null) screenshots = new MultipartFile[0];

        var updated = publishService.uploadAssets(requestId, appIcon, screenshots, ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Assets uploaded",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(updated)
        ));
    }

    @PostMapping("/{requestId}/submit")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> submit(
            HttpServletRequest request,
            @PathVariable Long requestId
    ) {
        String t = token(request);
        Long ownerAdminId = jwtUtil.extractAdminId(t);

        var submitted = publishService.submitForReview(requestId, ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Submitted for review",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(submitted)
        ));
    }
}