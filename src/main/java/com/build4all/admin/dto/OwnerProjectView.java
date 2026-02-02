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

 Long getLinkId();

 Long getProjectId();
 String getProjectName();

 String getSlug();
 String getAppName();

 String getStatus();

 String getApkUrl();
 String getIpaUrl();
 String getBundleUrl();

 // ✅ ADD THESE
 String getLogoUrl();

 String getAndroidPackageName();
 String getIosBundleId();
}
