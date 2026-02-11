package com.build4all.admin.dto;

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

	  String getLogoUrl();

	  String getAndroidPackageName();
	  String getIosBundleId();

	  // ✅ NEW: latest job status + error per platform
	  String getAndroidBuildStatus();
	  String getAndroidBuildError();

	  String getIosBuildStatus();
	  String getIosBuildError();
	}
