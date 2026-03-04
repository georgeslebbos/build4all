package com.build4all.importer.web;

import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ExcelValidationResult;
import com.build4all.importer.model.ImportOptions;
import com.build4all.importer.model.ReplaceScope;
import com.build4all.importer.service.ExcelSeederService;
import com.build4all.importer.service.TenantContextResolver;
import com.build4all.licensing.guard.OwnerSubscriptionGuard;
import com.build4all.webSocket.service.WebSocketEventService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/import")
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
public class ExcelImportController {

    private final ExcelSeederService service;
    private final TenantContextResolver tenantContextResolver;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;
    private final WebSocketEventService wsEvents;

    public ExcelImportController(
            ExcelSeederService service,
            TenantContextResolver tenantContextResolver,
            OwnerSubscriptionGuard ownerSubscriptionGuard,
            WebSocketEventService wsEvents
    ) {
        this.service = service;
        this.tenantContextResolver = tenantContextResolver;
        this.ownerSubscriptionGuard = ownerSubscriptionGuard;
        this.wsEvents = wsEvents;
    }

    // ✅ ADD THIS ENDPOINT (your Flutter app is calling it)
    @PostMapping("/excel/validate")
    public ResponseEntity<?> validateExcel(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        Long ownerProjectId = tenantContextResolver.resolveOwnerProjectId(request);

        // Optional: you can allow validate even if plan blocks writing.
        // If you want to block it, keep this guard:
        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) return blocked;

        ExcelValidationResult result = service.validateExcel(file);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/excel")
    public ResponseEntity<?> importExcel(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "replace", defaultValue = "false") boolean replace,
            @RequestParam(name = "replaceScope", defaultValue = "TENANT") ReplaceScope replaceScope
    ) throws Exception {

        Long ownerProjectId = tenantContextResolver.resolveOwnerProjectId(request);

        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) return blocked;

        ExcelImportResult result =
                service.importExcel(file, new ImportOptions(replace, replaceScope), ownerProjectId);

      
        wsEvents.sendImportCompleted(ownerProjectId, result);
        return ResponseEntity.ok(result);
    }
}