package com.build4all.app.dto;

public class AppSupportInfoDto {
    private Long linkId;
    private Long ownerId;
    private String ownerName;
    private String email;
    private String phoneNumber;

    public AppSupportInfoDto(Long linkId, Long ownerId, String ownerName, String email, String phoneNumber) {
        this.linkId = linkId;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public Long getLinkId() { return linkId; }
    public Long getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
}
