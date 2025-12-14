package com.build4all.business.dto;

import com.build4all.business.domain.BusinessUser;

/**
 * BusinessUserSimpleDto
 * ------------------------------------------------------------
 * A lightweight DTO used to expose ONLY the basic BusinessUser info
 * needed in lists/dropdowns (ex: managers list, assigned users list).
 *
 * ✅ Not a JPA entity (no @Entity). This is returned to the frontend.
 *
 * Why "Simple"?
 * - Avoid sending the whole BusinessUser entity (which may include lazy relations).
 * - Reduce payload size.
 * - Prevent accidental exposure of sensitive fields (passwordHash, tokens...).
 */
public class BusinessUserSimpleDto {

    /** Primary key of business_user table */
    private Long id;

    /** User first name (kept as "firstname" to match your existing API/FE contract) */
    private String firstname;

    /** User last name (kept as "lastname" to match your existing API/FE contract) */
    private String lastname;

    /** Email used for login/communication (can be null depending on your business rules) */
    private String email;

    /** ✅ ADDED: phone number for contact / login alternative */
    private String phoneNumber;

    /** Business name pulled from the parent Businesses entity */
    private String businessName;

    /**
     * Map BusinessUser entity -> Simple DTO.
     *
     * ⚠️ Important notes:
     * 1) user.getBusiness() is LAZY in your entity:
     *    @ManyToOne(fetch = FetchType.LAZY)
     *    If you build this DTO OUTSIDE a transaction, you may get LazyInitializationException.
     *    Fix options:
     *    - Build DTO inside @Transactional service method
     *    - Or fetch business with JOIN FETCH in repository query
     *
     * 2) This DTO intentionally does NOT include passwordHash / role / googleId / etc.
     */
    public BusinessUserSimpleDto(BusinessUser user) {
        this.id = user.getId();
        this.firstname = user.getFirstName();
        this.lastname = user.getLastName();
        this.email = user.getEmail();

        // ✅ ADDED: safe primitive field from BusinessUser itself
        this.phoneNumber = user.getPhoneNumber();

        // Business name comes from the related Businesses entity
        // If business is not loaded (lazy) and session is closed -> may fail (see notes above).
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
