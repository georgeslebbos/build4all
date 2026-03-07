package com.build4all.feeders.clients;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.catalog.domain.*;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.*;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.feeders.clients.NeroliGlowEcommerceSeeder.CategorySeed;
import com.build4all.feeders.clients.NeroliGlowEcommerceSeeder.ItemTypeSeed;
import com.build4all.feeders.clients.NeroliGlowEcommerceSeeder.ProductSeed;
import com.build4all.feeders.clients.NeroliGlowEcommerceSeeder.SeedDataset;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.promo.repository.CouponRepository;
import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
import com.build4all.shipping.domain.ShippingMethod;
import com.build4all.shipping.domain.ShippingMethodType;
import com.build4all.shipping.repository.ShippingMethodRepository;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.repository.TaxRuleRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Configuration("NestleWatersLebanonSeeder")
@Profile("seednestle")
public class NestleWatersLebanonSeeder {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_UPCOMING = "UPCOMING";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    @Value("classpath:seed/nestlewaters_lb_seed_dataset.json")
    private Resource seedJson;

    @Bean
    public CommandLineRunner seedNestleWaters(
            ObjectMapper mapper,
            PasswordEncoder passwordEncoder,

            ProjectRepository projectRepo,
            CategoryRepository categoryRepo,
            ItemTypeRepository itemTypeRepo,
            RoleRepository roleRepo,
            AdminUsersRepository adminRepo,
            AdminUserProjectRepository aupRepo,
            CurrencyRepository currencyRepo,
            ProductRepository productRepo,
            ItemStatusRepository itemStatusRepo,

            TaxRuleRepository taxRuleRepo,
            CountryRepository countryRepo,
            RegionRepository regionRepo,
            ShippingMethodRepository shippingRepo,
            CouponRepository couponRepo
    ) {
        return args -> {

            System.out.println("🧪 NestleWaters JSON seeder running (profile=seednestle) ...");

            SeedDataset data = mapper.readValue(seedJson.getInputStream(), SeedDataset.class);

            Project project = projectRepo.findByProjectNameIgnoreCase(data.projectName)
                    .orElseGet(() -> {
                        Project p = new Project();
                        p.setProjectName(data.projectName);
                        p.setDescription(data.projectDescription);
                        p.setActive(true);
                        return projectRepo.save(p);
                    });

            Map<String, Category> categoriesByName = new HashMap<>();
            for (CategorySeed cs : data.categories) {

                Category c = categoryRepo.findByNameIgnoreCaseAndProject_Id(cs.name, project.getId())
                        .orElseGet(() -> {

                            Category created = new Category();
                            created.setProject(project);
                            created.setName(cs.name);
                            created.setIconName(cs.iconName);
                            created.setIconLibrary(cs.iconLibrary);

                            return categoryRepo.save(created);
                        });

                categoriesByName.put(cs.name.toUpperCase(), c);
            }

            Map<String, ItemType> itemTypesByName = new HashMap<>();

            for (ItemTypeSeed its : data.itemTypes) {

                Category cat = categoriesByName.get(its.categoryName.toUpperCase());

                if (cat == null) {
                    throw new IllegalStateException("Missing category for itemType: " + its.name);
                }

                ItemType t = itemTypeRepo.findByName(its.name)
                        .orElseGet(() -> {

                            ItemType created = new ItemType();
                            created.setName(its.name);
                            created.setIcon(its.iconName);
                            created.setIconLibrary(its.iconLibrary);
                            created.setCategory(cat);
                            created.setDefaultForCategory(its.defaultForCategory);

                            return itemTypeRepo.save(created);
                        });

                itemTypesByName.put(its.name.toLowerCase(), t);
            }

            Role ownerRole = roleRepo.findByNameIgnoreCase("OWNER")
                    .orElseGet(() -> roleRepo.save(new Role("OWNER")));

            AdminUser owner = adminRepo.findByEmail(data.owner.email)
                    .orElseGet(() -> {

                        AdminUser a = new AdminUser();
                        a.setUsername(data.owner.username);
                        a.setFirstName(data.owner.firstName);
                        a.setLastName(data.owner.lastName);
                        a.setEmail(data.owner.email);
                        a.setPasswordHash(passwordEncoder.encode(data.owner.password));
                        a.setRole(ownerRole);

                        return adminRepo.save(a);
                    });

            AdminUserProject aup = aupRepo
                    .findByAdmin_AdminIdAndProject_IdAndSlug(owner.getAdminId(), project.getId(), data.tenant.slug)
                    .orElseGet(() -> {

                        AdminUserProject link = new AdminUserProject();
                        link.setAdmin(owner);
                        link.setProject(project);
                        link.setSlug(data.tenant.slug);
                        link.setAppName(data.tenant.appName);
                        link.setStatus(data.tenant.status != null ? data.tenant.status : "ACTIVE");
                        link.setValidFrom(LocalDate.now());
                        link.setEndTo(LocalDate.now().plusYears(1));

                        return aupRepo.save(link);
                    });

            Currency currency = currencyRepo.findByCodeIgnoreCase(data.tenant.currencyCode)
                    .orElseThrow(() -> new IllegalStateException("Currency not found"));

            List<Product> existingProducts = productRepo.findByOwnerProject_Id(aup.getId());

            Set<String> existingSkus = new HashSet<>();

            for (Product p : existingProducts) {
                if (p.getSku() != null) {
                    existingSkus.add(p.getSku().toUpperCase());
                }
            }

            int insertedProducts = 0;

            for (ProductSeed ps : data.products) {

                if (ps.sku != null && existingSkus.contains(ps.sku.toUpperCase())) {
                    continue;
                }

                ItemType itemType = itemTypesByName.get(ps.itemTypeName.toLowerCase());

                if (itemType == null) {
                    throw new IllegalStateException("Missing itemType for product: " + ps.name);
                }

                Product p = new Product();

                p.setOwnerProject(aup);
                p.setItemType(itemType);
                p.setItemName(ps.name);
                p.setDescription(ps.description);
                p.setPrice(ps.price != null ? ps.price : BigDecimal.ZERO);
                p.setCurrency(currency);
                p.setStock(ps.stock != null ? ps.stock : 0);

                p.setStatus(resolveStatusForSeed(itemStatusRepo, ps.status));

                p.setImageUrl(ps.imageUrl);
                p.setSku(ps.sku);

                p.setProductType(ps.productType != null
                        ? ProductType.valueOf(ps.productType)
                        : ProductType.SIMPLE);

                p.setVirtualProduct(Boolean.TRUE.equals(ps.virtualProduct));
                p.setDownloadable(Boolean.TRUE.equals(ps.downloadable));

                productRepo.save(p);

                insertedProducts++;
            }

            System.out.println("✅ NestleWaters seeding complete.");
            System.out.println("Inserted products: " + insertedProducts);
        };
    }

    private static ItemStatus resolveStatusForSeed(ItemStatusRepository repo, String rawStatus) {

        String code = mapLegacyStatusCode(rawStatus);

        return repo.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("ItemStatus not found: " + code));
    }

    private static String mapLegacyStatusCode(String rawStatus) {

        if (rawStatus == null || rawStatus.isBlank()) {
            return STATUS_DRAFT;
        }

        String v = rawStatus.trim().toUpperCase(Locale.ROOT);

        return switch (v) {
            case "DRAFT" -> STATUS_DRAFT;
            case "UPCOMING", "COMING_SOON" -> STATUS_UPCOMING;
            case "PUBLISHED", "ACTIVE", "AVAILABLE", "LIVE" -> STATUS_PUBLISHED;
            case "ARCHIVED" -> STATUS_ARCHIVED;
            default -> STATUS_DRAFT;
        };
    }
}