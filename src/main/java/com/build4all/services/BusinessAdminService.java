package com.build4all.services;
import com.build4all.repositories.*;
import com.build4all.entities.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BusinessAdminService {

    @Autowired
    private BusinessAdminsRepository businessAdminRepository;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private BusinessService businessService;

    public void assignAdminToBusiness(Long adminId, Long businessId) {
        AdminUsers admin = adminUserService.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        Businesses business = businessService.findById(businessId);
        if (business == null) {
            throw new RuntimeException("Business not found");
        }

        BusinessAdmins businessAdmin = new BusinessAdmins(business, admin);
        businessAdminRepository.save(businessAdmin);
    }
}
