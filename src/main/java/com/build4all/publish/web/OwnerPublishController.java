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
        // You already have this helper, keep it
        return jwtUtil.extractTokenFromRequest(request);
    }

    /**
     * Optional security hardening:
     * If your JWT contains tenant/aup claim, enforce it here
     * so owner cannot draft publish for someone else’s aupId.
     *
     * Uncomment + adapt based on your JwtUtil API.
     */
    // private void requireAupScope(HttpServletRequest request, Long aupId) {
    //     String t = token(request);
    //     jwtUtil.requireTenantMatch(t, aupId); // example (adapt)
    // }

    @PostMapping("/draft")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> getOrCreateDraft(
            HttpServletRequest request,
            @Valid @RequestBody CreatePublishDraftDto dto
    ) {
        String t = token(request);
        Long ownerAdminId = jwtUtil.extractId(t);

        // requireAupScope(request, dto.getAupId()); // optional strict tenant lock

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
        Long ownerAdminId = jwtUtil.extractId(t);

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
        Long ownerAdminId = jwtUtil.extractId(t);

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
        Long ownerAdminId = jwtUtil.extractId(t);

        var submitted = publishService.submitForReview(requestId, ownerAdminId);

        return ResponseEntity.ok(Map.of(
                "message", "Submitted for review",
                "data", com.build4all.publish.dto.AppPublishMapper.toDto(submitted)
        ));
    }
}