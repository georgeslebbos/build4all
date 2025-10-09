package com.build4all.admin.service;
import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserBusiness;
import com.build4all.business.domain.Businesses;
import com.build4all.admin.repository.AdminUserBusinessRepository;
import com.build4all.business.service.BusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminUserBusinessService
{

    @Autowired
    private AdminUserBusinessRepository businessAdminRepository;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private BusinessService businessService;

    public void assignAdminToBusiness(Long adminId, Long businessId) {
        AdminUser admin = adminUserService.findById(adminId)
            .orElseThrow(() -> new RuntimeException("Admin not found"));

        Businesses business = businessService.findById(businessId);
        if (business == null) {
            throw new RuntimeException("Business not found");
        }

        AdminUserBusiness businessAdmin = new AdminUserBusiness(business, admin);
        businessAdminRepository.save(businessAdmin);
    }
}
