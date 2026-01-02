// File: src/main/java/com/build4all/feeders/importer/ReplaceService.java
package com.build4all.importer.service;

import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.importer.model.ReplaceScope;
import com.build4all.promo.repository.CouponRepository;
import com.build4all.shipping.repository.ShippingMethodRepository;
import com.build4all.tax.repository.TaxRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplaceService {

    private final ProductRepository productRepo;
    private final TaxRuleRepository taxRuleRepo;
    private final ShippingMethodRepository shippingRepo;
    private final CouponRepository couponRepo;

    // Only used in FULL scope:
    private final ItemTypeRepository itemTypeRepo;
    private final CategoryRepository categoryRepo;

    public ReplaceService(
            ProductRepository productRepo,
            TaxRuleRepository taxRuleRepo,
            ShippingMethodRepository shippingRepo,
            CouponRepository couponRepo,
            ItemTypeRepository itemTypeRepo,
            CategoryRepository categoryRepo
    ) {
        this.productRepo = productRepo;
        this.taxRuleRepo = taxRuleRepo;
        this.shippingRepo = shippingRepo;
        this.couponRepo = couponRepo;
        this.itemTypeRepo = itemTypeRepo;
        this.categoryRepo = categoryRepo;
    }

    @Transactional
    public void replace(Long projectId, Long ownerProjectId, ReplaceScope scope) {
        // Tenant-scoped delete (safe)
        /*couponRepo.deleteByOwnerProjectId(ownerProjectId);
        shippingRepo.deleteByOwnerProject_Id(ownerProjectId);
        taxRuleRepo.deleteByOwnerProject_Id(ownerProjectId);
        productRepo.deleteByOwnerProject_Id(ownerProjectId);

        if (scope == ReplaceScope.FULL) {
            // Project-scoped delete (danger if multiple tenants share project)
            itemTypeRepo.deleteByCategory_Project_Id(projectId);
            categoryRepo.deleteByProject_Id(projectId);
        }*/
    }
}
