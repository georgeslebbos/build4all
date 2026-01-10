package com.build4all.importer.service;

import com.build4all.importer.dto.SeedDataset;
import com.build4all.importer.importer.DatasetImporter;
import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ImportOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core importer orchestration for existing tenant.
 *
 * ✅ resolves tenant by ownerProjectId (not Excel)
 * ✅ optional replace
 * ✅ import dataset
 */
@Service
public class ExcelImportCore {

    private final ExistingTenantResolver tenantResolver;
    private final ReplaceService replaceService;
    private final DatasetImporter datasetImporter;

    public ExcelImportCore(
            ExistingTenantResolver tenantResolver,
            ReplaceService replaceService,
            DatasetImporter datasetImporter
    ) {
        this.tenantResolver = tenantResolver;
        this.replaceService = replaceService;
        this.datasetImporter = datasetImporter;
    }

    @Transactional
    public ExcelImportResult importDataset(SeedDataset data, ImportOptions opts, Long ownerProjectId) {

        ExistingTenantResolver.Resolved resolved = tenantResolver.resolveExisting(ownerProjectId);

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
