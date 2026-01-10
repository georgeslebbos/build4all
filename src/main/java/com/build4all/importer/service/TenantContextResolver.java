package com.build4all.importer.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves ownerProjectId (tenant) from the request context.
 * Recommended: from JWT claim "ownerProjectId".
 */
public interface TenantContextResolver {
    Long resolveOwnerProjectId(HttpServletRequest request);
}
