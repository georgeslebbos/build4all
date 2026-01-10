package com.build4all.importer.importer;

import com.build4all.importer.dto.SeedDataset;
import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.service.ExistingTenantResolver;

public interface DatasetImporter {

    /**
     * Import dataset into an existing tenant (AdminUserProject)
     */
    ExcelImportResult importAll(SeedDataset data, ExistingTenantResolver.Resolved resolved);
}
