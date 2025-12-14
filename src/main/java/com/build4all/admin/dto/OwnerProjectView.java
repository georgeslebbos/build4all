// src/main/java/com/build4all/admin/dto/OwnerProjectView.java
package com.build4all.admin.dto;

/**
 * Projection interface used by Spring Data JPA to return a "slim" view of Owner Projects.
 *
 * Instead of loading full AdminUserProject and Project entities (which may include many relations),
 * Spring will map the selected columns from a @Query directly into this interface.
 *
 * Important:
 * - Method names MUST match the selected aliases in the JPQL query:
 *   e.g. "l.id as linkId"  -> getLinkId()
 *        "p.id as projectId" -> getProjectId()
 *        "p.projectName as projectName" -> getProjectName()
 * - This is used for faster listing screens (dashboard tables/cards).
 */
public interface OwnerProjectView {

    // NEW: the AdminUserProject link row identifier (aup_id).
    Long getLinkId();

    // The project identifier.
    Long getProjectId();

    // Human-readable project name.
    String getProjectName();

    // The app slug used for routing/tenant identification.
    String getSlug();

    // Display name of the app instance.
    String getAppName();

    // NEW: status of the app assignment (ACTIVE / SUSPENDED / EXPIRED / DELETED).
    String getStatus();

    // Latest Android build artifact URL.
    String getApkUrl();

    // Latest iOS build artifact URL.
    String getIpaUrl();

    // Additional artifact URL (e.g., bundle / AAB / web bundle depending on pipeline).
    String getBundleUrl();
}
