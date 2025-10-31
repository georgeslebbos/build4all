package com.build4all.business.dto;

import com.build4all.business.domain.BusinessUser;

public class BusinessUserSimpleDto {
    private Long id;
    private String firstname;
    private String lastname;
    private String email;
    private String phoneNumber;     // ✅ ADDED
    private String businessName;

    public BusinessUserSimpleDto(BusinessUser user) {
        this.id = user.getId();
        this.firstname = user.getFirstName();
        this.lastname = user.getLastName();
        this.email = user.getEmail();
        this.phoneNumber = user.getPhoneNumber();    // ✅ ADDED
        this.businessName = user.getBusiness().getBusinessName();
    }

    // --- Getters ---
    public Long getId() {
        return id;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {   // ✅ ADDED
        return phoneNumber;
    }

    public String getBusinessName() {
        return businessName;
    }
}
