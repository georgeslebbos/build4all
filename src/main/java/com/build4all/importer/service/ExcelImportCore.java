// File: src/main/java/com/build4all/feeders/importer/ExcelImportCore.java
package com.build4all.importer.service;

import com.build4all.importer.dto.SeedDataset;
import com.build4all.importer.importer.DatasetImporter;
import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ImportOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelImportCore {

    private final TenantResolver tenantResolver;
    private final ReplaceService replaceService;
    private final DatasetImporter datasetImporter;

    public ExcelImportCore(
            TenantResolver tenantResolver,
            ReplaceService replaceService,
            DatasetImporter datasetImporter
    ) {
        this.tenantResolver = tenantResolver;
        this.replaceService = replaceService;
        this.datasetImporter = datasetImporter;
    }

    @Transactional
    public ExcelImportResult importDataset(SeedDataset data, ImportOptions opts) {
        TenantResolver.Resolved resolved = tenantResolver.resolveOrCreate(data);

        if (opts.replace()) {
            replaceService.replace(resolved.projectId(), resolved.ownerProjectId(), opts.replaceScope());
        }

        ExcelImportResult result = datasetImporter.importAll(data, resolved);
        result.projectId = resolved.projectId();
        result.ownerProjectId = resolved.ownerProjectId();
        result.slug = resolved.slug();
        return result;
    }
}
