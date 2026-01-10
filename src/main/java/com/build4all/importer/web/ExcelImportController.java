package com.build4all.importer.web;

import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ExcelValidationResult;
import com.build4all.importer.model.ImportOptions;
import com.build4all.importer.model.ReplaceScope;
import com.build4all.importer.service.ExcelSeederService;
import com.build4all.importer.service.TenantContextResolver;
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

    public ExcelImportController(ExcelSeederService service, TenantContextResolver tenantContextResolver) {
        this.service = service;
        this.tenantContextResolver = tenantContextResolver;
    }

    @PostMapping("/excel/validate")
    public ResponseEntity<ExcelValidationResult> validate(
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        return ResponseEntity.ok(service.validateExcel(file));
    }

    @PostMapping("/excel")
    public ResponseEntity<ExcelImportResult> importExcel(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "replace", defaultValue = "false") boolean replace,
            @RequestParam(name = "replaceScope", defaultValue = "TENANT") ReplaceScope replaceScope
    ) throws Exception {

        Long ownerProjectId = tenantContextResolver.resolveOwnerProjectId(request);

        return ResponseEntity.ok(
                service.importExcel(file, new ImportOptions(replace, replaceScope), ownerProjectId)
        );
    }
}
