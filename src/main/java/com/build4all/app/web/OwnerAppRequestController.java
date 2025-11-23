package com.build4all.app.web;

import com.build4all.app.domain.AppRequest;
import com.build4all.app.dto.CreateAppRequestDto;
import com.build4all.app.service.AppRequestService;
import com.build4all.app.service.ThemeJsonBuilder;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.admin.domain.AdminUserProject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner")
public class OwnerAppRequestController {

    private final AppRequestService service;
    private final AppRequestRepository appRequestRepo;
    private final AdminUserProjectRepository aupRepo;

    // Optional: expose callback data in the response for GitHub Action wiring
    @Value("${ci.callbackUrl:}")
    private String callbackBase;

    @Value("${ci.callbackToken:}")
    private String callbackToken;

    public OwnerAppRequestController(AppRequestService service,
                                     AppRequestRepository appRequestRepo,
                                     AdminUserProjectRepository aupRepo) {
        this.service = service;
        this.appRequestRepo = appRequestRepo;
        this.aupRepo = aupRepo;
    }

    // ----- Legacy JSON create (kept) -----
    @PostMapping(
    	    value = "/app-requests",
    	    consumes = MediaType.APPLICATION_JSON_VALUE,
    	    produces = MediaType.APPLICATION_JSON_VALUE
    	)
    	public ResponseEntity<?> create(@RequestParam Long ownerId,
    	                                @RequestBody CreateAppRequestDto dto) {
    	    try {
    	        AppRequest r = service.createRequest(
    	            ownerId,
    	            dto.projectId(),
    	            dto.appName(),
    	            dto.slug(),
    	            dto.logoUrl(),
    	            dto.themeId(),
    	            dto.themeJson(),
    	            dto.notes()
    	        );

    	        Map<String, Object> body = new HashMap<>();
    	        body.put("message", "Request created");
    	        body.put("requestId", r.getId());
    	        body.put("status", r.getStatus());
    	        body.put("appName", nz(r.getAppName()));
    	        body.put("slug", nz(r.getSlug()));
    	        return ResponseEntity.ok(body);
    	    } catch (Exception ex) {
    	        return ResponseEntity.internalServerError().body(Map.of(
    	            "error", "Internal error",
    	            "details", ex.getClass().getSimpleName()
    	        ));
    	    }
    	}

    // ----- AUTO flow (multipart): creates + approves + triggers CI -----
    @PostMapping(
            value = "/app-requests/auto",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createAndAutoApprove(
            @RequestParam Long ownerId,
            @RequestParam Long projectId,
            @RequestParam String appName,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) Long themeId,
            @RequestParam(required = false) String notes,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "logo", required = false) MultipartFile logo,

            // raw palette from Flutter
            @RequestParam(required = false) String primaryColor,
            @RequestParam(required = false) String secondaryColor,
            @RequestParam(required = false) String backgroundColor,
            @RequestParam(required = false) String onBackgroundColor,
            @RequestParam(required = false) String errorColor
    ) {
        try {
            MultipartFile logoFile = (file != null) ? file : logo;

            // 👇 هون بنبني JSON مرّة وحدة وبنضمن الـ keys
            String themeJson = ThemeJsonBuilder.buildThemeJson(
                    primaryColor,
                    secondaryColor,
                    backgroundColor,
                    onBackgroundColor,
                    errorColor
            );

            AdminUserProject link = service.createAndAutoApprove(
                    ownerId,
                    projectId,
                    appName,
                    slug,
                    logoFile,
                    themeId,
                    notes,
                    themeJson
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", "APK build started");
            body.put("adminId", ownerId);
            body.put("projectId", projectId);
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));
            body.put("status", nz(link.getStatus()));
            body.put("licenseId", nz(link.getLicenseId()));
            body.put("themeId", link.getThemeId());
            body.put("validFrom", link.getValidFrom());
            body.put("endTo", link.getEndTo());
            body.put("logoUrl", nz(link.getLogoUrl()));
            body.put("apkUrl", nz(link.getApkUrl()));
            body.put("themeJson", themeJson);

            String manifestUrlGuess =
                    "https://raw.githubusercontent.com/fatimahh0/HobbySphereFlutter/main/builds/"
                            + ownerId + "/" + projectId + "/" + link.getSlug() + "/latest.json";
            body.put("manifestUrlHint", manifestUrlGuess);

            body.put("callbackBase", nz(callbackBase));
            body.put("callbackToken", nz(callbackToken));

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }


    @GetMapping(value = "/app-requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AppRequest> myRequests(@RequestParam Long ownerId) {
        return appRequestRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @GetMapping(value = "/my-apps", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OwnerProjectView> myApps(@RequestParam Long ownerId) {
        return aupRepo.findOwnerProjectsSlim(ownerId);
    }
    
    @PostMapping(
    	    value = "/links/{linkId}/rebuild",
    	    produces = MediaType.APPLICATION_JSON_VALUE
    	)
    	public ResponseEntity<?> rebuildByLink(
    	        @PathVariable Long linkId,
    	        @RequestParam Long ownerId
    	) {
    	    try {
    	        AdminUserProject link = service.prepareRebuildByLink(ownerId, linkId);
    	        String jobId = service.enqueueBuild(link.getAdminId(), link.getProjectId(), link);

    	        Map<String, Object> body = new HashMap<>();
    	        body.put("status", "QUEUED");
    	        body.put("message", "APK rebuild queued.");
    	        body.put("ownerProjectLinkId", link.getId());
    	        body.put("projectId", link.getProjectId());
    	        body.put("slug", nz(link.getSlug()));
    	        body.put("apkUrl", nz(link.getApkUrl()));
    	        body.put("jobId", jobId);
    	        body.put("callbackBase", nz(callbackBase));
    	        body.put("callbackToken", nz(callbackToken));
    	        return ResponseEntity.accepted().body(body);

    	    } catch (IllegalArgumentException | IllegalStateException ex) {
    	        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    	    } catch (Exception ex) {
    	        return ResponseEntity.internalServerError().body(Map.of(
    	            "error", "Internal error",
    	            "details", ex.getClass().getSimpleName()
    	        ));
    	    }
    	}


    private static String nz(String s) { return (s == null) ? "" : s; }
}
