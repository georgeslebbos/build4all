package com.build4all.importer.web;

import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ExcelValidationResult;
import com.build4all.importer.model.ImportOptions;
import com.build4all.importer.model.ReplaceScope;
import com.build4all.importer.service.ExcelSeederService;
import com.build4all.importer.service.TenantContextResolver;
import com.build4all.licensing.guard.OwnerSubscriptionGuard;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel Import API (Existing Tenant)
 *
 * ✅ SETUP sheet is NOT used.
 * ✅ Tenant is resolved by ownerProjectId from JWT token.
 *
 * POST /api/admin/import/excel/validate   => validates file structure and FK consistency
 * POST /api/admin/import/excel           => imports into existing tenant (ownerProjectId from JWT)
 */
@RestController
@RequestMapping("/api/admin/import")
public class ExcelImportController {

    private final ExcelSeederService service;
    private final TenantContextResolver tenantContextResolver;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;
    
    public ExcelImportController(ExcelSeederService service,
            TenantContextResolver tenantContextResolver,
            OwnerSubscriptionGuard ownerSubscriptionGuard) {
this.service = service;
this.tenantContextResolver = tenantContextResolver;
this.ownerSubscriptionGuard = ownerSubscriptionGuard;
}
    @PostMapping("/excel")
    public ResponseEntity<ExcelImportResult> importExcel(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "replace", defaultValue = "false") boolean replace,
            @RequestParam(name = "replaceScope", defaultValue = "TENANT") ReplaceScope replaceScope
    ) throws Exception {

        Long ownerProjectId = tenantContextResolver.resolveOwnerProjectId(request);

        // ✅ NEW subscription guard
        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) {
            // method returns ResponseEntity<ExcelImportResult>, so cast is awkward.
            // easiest: change method signature to ResponseEntity<?> (recommended).
            return (ResponseEntity<ExcelImportResult>) blocked;
        }

        return ResponseEntity.ok(
                service.importExcel(file, new ImportOptions(replace, replaceScope), ownerProjectId)
        );
    }
}
