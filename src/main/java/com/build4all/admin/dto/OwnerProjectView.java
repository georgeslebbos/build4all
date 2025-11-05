package com.build4all.admin.dto;

public interface OwnerProjectView {
    Long getProjectId();
    String getProjectName();
    String getSlug();
    String getAppName();   // ⬅️ NEW
    String getApkUrl();
    String getIpaUrl(); // NEW
}
