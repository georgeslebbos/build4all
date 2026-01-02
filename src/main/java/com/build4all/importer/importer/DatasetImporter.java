// File: src/main/java/com/build4all/feeders/importer/DatasetImporter.java
package com.build4all.importer.importer;

import com.build4all.importer.dto.SeedDataset;
import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.service.TenantResolver;

public interface DatasetImporter {
    ExcelImportResult importAll(SeedDataset data, TenantResolver.Resolved resolved);
}
