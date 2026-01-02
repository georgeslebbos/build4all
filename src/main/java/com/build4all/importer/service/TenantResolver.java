// File: src/main/java/com/build4all/feeders/importer/TenantResolver.java
package com.build4all.importer.service;

import com.build4all.importer.dto.SeedDataset;

public interface TenantResolver {

    /**
     * Creates/returns:
     * - Project
     * - Owner AdminUser
     * - AdminUserProject (tenant link)
     */
    Resolved resolveOrCreate(SeedDataset data);

    record Resolved(Long projectId, Long ownerProjectId, String slug) {}
}
