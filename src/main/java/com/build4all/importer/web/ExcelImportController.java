package com.build4all.importer.web;

import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ExcelValidationResult;
import com.build4all.importer.model.ImportOptions;
import com.build4all.importer.model.ReplaceScope;
import com.build4all.importer.service.ExcelSeederService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/import")
public class ExcelImportController {

    private final ExcelSeederService service;

    public ExcelImportController(ExcelSeederService service) {
        this.service = service;
    }

    @PostMapping("/excel/validate")
    public ResponseEntity<ExcelValidationResult> validate(@RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(service.validateExcel(file));
    }

    @PostMapping("/excel")
    public ResponseEntity<ExcelImportResult> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "replace", defaultValue = "false") boolean replace,
            @RequestParam(name = "replaceScope", defaultValue = "TENANT") ReplaceScope replaceScope
    ) throws Exception {
        return ResponseEntity.ok(service.importExcel(file, new ImportOptions(replace, replaceScope)));
    }
}
