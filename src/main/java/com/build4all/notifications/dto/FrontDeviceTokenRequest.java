package com.build4all.notifications.dto;

import com.build4all.notifications.domain.DevicePlatform;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FrontDeviceTokenRequest {

    private Long ownerProjectLinkId;

    private String fcmToken;

    private DevicePlatform platform;

    private String packageName;

    private String bundleId;

    private String deviceId;

	public Long getOwnerProjectLinkId() {
		return ownerProjectLinkId;
	}

	public void setOwnerProjectLinkId(Long ownerProjectLinkId) {
		this.ownerProjectLinkId = ownerProjectLinkId;
	}

	public String getFcmToken() {
		return fcmToken;
	}

	public void setFcmToken(String fcmToken) {
		this.fcmToken = fcmToken;
	}

	public DevicePlatform getPlatform() {
		return platform;
	}

	public void setPlatform(DevicePlatform platform) {
		this.platform = platform;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getBundleId() {
		return bundleId;
	}

	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	
}