package com.build4all.importer.web;

import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ImportOptions;
import com.build4all.importer.model.ReplaceScope;
import com.build4all.importer.service.ExcelSeederService;
import com.build4all.importer.service.TenantContextResolver;
import com.build4all.licensing.guard.OwnerSubscriptionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel Import API (Existing Tenant)
 *
 * ✅ SETUP sheet is NOT used.
 * ✅ Tenant is resolved by ownerProjectId from JWT token.
 *
 * POST /api/admin/import/excel   => imports into existing tenant (ownerProjectId from JWT)
 */
@RestController
@RequestMapping("/api/admin/import")
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
public class ExcelImportController {

    private final ExcelSeederService service;
    private final TenantContextResolver tenantContextResolver;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;

    public ExcelImportController(
            ExcelSeederService service,
            TenantContextResolver tenantContextResolver,
            OwnerSubscriptionGuard ownerSubscriptionGuard
    ) {
        this.service = service;
        this.tenantContextResolver = tenantContextResolver;
        this.ownerSubscriptionGuard = ownerSubscriptionGuard;
    }

    @PostMapping("/excel")
    public ResponseEntity<?> importExcel(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "replace", defaultValue = "false") boolean replace,
            @RequestParam(name = "replaceScope", defaultValue = "TENANT") ReplaceScope replaceScope
    ) throws Exception {

        // ✅ tenant from token (NO request param)
        Long ownerProjectId = tenantContextResolver.resolveOwnerProjectId(request);

        // ✅ subscription guard (clean return type)
        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) return blocked;

        ExcelImportResult result =
                service.importExcel(file, new ImportOptions(replace, replaceScope), ownerProjectId);

        return ResponseEntity.ok(result);
    }
}