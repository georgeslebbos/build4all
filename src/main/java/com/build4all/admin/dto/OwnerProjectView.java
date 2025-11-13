// src/main/java/com/build4all/admin/dto/OwnerProjectView.java
package com.build4all.admin.dto;

public interface OwnerProjectView {
    Long getLinkId();       // NEW
    Long getProjectId();
    String getProjectName();
    String getSlug();
    String getAppName();
    String getStatus();     // NEW
    String getApkUrl();
    String getIpaUrl();
    String getBundleUrl();
}
