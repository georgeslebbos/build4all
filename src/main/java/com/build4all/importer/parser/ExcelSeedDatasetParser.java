// File: src/main/java/com/build4all/feeders/importer/ExcelSeedDatasetParser.java
package com.build4all.importer.parser;

import com.build4all.importer.dto.SeedDataset;

import java.io.InputStream;

public interface ExcelSeedDatasetParser {
    SeedDataset parse(InputStream in) throws Exception;
}
