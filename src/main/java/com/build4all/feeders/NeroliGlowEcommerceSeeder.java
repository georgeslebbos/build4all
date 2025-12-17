package com.build4all.feeders;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
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
import java.util.*;

/**
 * NeroliGlow seed data loader (JSON -> JPA repositories)
 *
 * Usage:
 *   1) Put JSON file under: src/main/resources/seed/neroliglow_seed_dataset.json
 *   2) Run with profile "seed":
 *        mvn spring-boot:run -Dspring-boot.run.profiles=seed
 *
 * This will:
 *  - Ensure Project, Categories, ItemTypes
 *  - Ensure OWNER Role + demo AdminUser + AdminUserProject (tenant)
 *  - Insert Products for that tenant (ownerProject)
 */
@Configuration
@Profile("seed")
public class NeroliGlowEcommerceSeeder {

    // ---------- JSON model ----------
    public static class SeedDataset {
        public String projectName;
        public String projectDescription;
        public Owner owner;
        public Tenant tenant;
        public List<CategorySeed> categories = new ArrayList<>();
        public List<ItemTypeSeed> itemTypes = new ArrayList<>();
        public List<ProductSeed> products = new ArrayList<>();
    }
    public static class Owner {
        public String username;
        public String firstName;
        public String lastName;
        public String email;
        public String password;
        public String role; // e.g. OWNER
    }
    public static class Tenant {
        public String slug;
        public String appName;
        public String status; // ACTIVE
        public String currencyCode; // USD
    }
    public static class CategorySeed {
        public String name;
        public String iconName;
        public String iconLibrary;
    }
    public static class ItemTypeSeed {
        public String name;
        public String categoryName;
        public String iconName;
        public String iconLibrary;
        public boolean defaultForCategory;
    }
    public static class ProductSeed {
        public String name;
        public String sku;
        public String productType;     // SIMPLE | VARIABLE | ...
        public String itemTypeName;    // must exist in itemTypes
        public BigDecimal price;
        public String currencyCode;    // USD
        public Integer stock;
        public String imageUrl;
        public String description;
    }

    @Value("classpath:seed/neroliglow_seed_dataset_URL.json")
    private Resource seedJson;

    @Bean
    public CommandLineRunner seedNeroliGlow(
            ObjectMapper mapper,
            PasswordEncoder passwordEncoder,
            ProjectRepository projectRepo,
            CategoryRepository categoryRepo,
            ItemTypeRepository itemTypeRepo,
            RoleRepository roleRepo,
            AdminUsersRepository adminRepo,
            AdminUserProjectRepository aupRepo,
            CurrencyRepository currencyRepo,
            ProductRepository productRepo
    ) {
        return args -> {
            System.out.println("🧪 NeroliGlow JSON seeder running (profile=seed) ...");

            SeedDataset data = mapper.readValue(seedJson.getInputStream(), SeedDataset.class);

            // 1) Project
            Project project = projectRepo.findByProjectNameIgnoreCase(data.projectName)
                    .orElseGet(() -> {
                        Project p = new Project();
                        p.setProjectName(data.projectName);
                        p.setDescription(data.projectDescription);
                        p.setActive(true);
                        return projectRepo.save(p);
                    });

            // 2) Categories (project-scoped)
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

            // 3) ItemTypes (global unique name, but linked to category)
            Map<String, ItemType> itemTypesByName = new HashMap<>();
            for (ItemTypeSeed its : data.itemTypes) {
                Category cat = categoriesByName.get(its.categoryName.toUpperCase());
                if (cat == null) {
                    throw new IllegalStateException("Missing category for itemType: " + its.name + " -> " + its.categoryName);
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

                // If it already existed, we still ensure it points to the correct category + default flag.
                boolean dirty = false;
                if (t.getCategory() == null || !Objects.equals(t.getCategory().getId(), cat.getId())) {
                    t.setCategory(cat);
                    dirty = true;
                }
                if (t.isDefaultForCategory() != its.defaultForCategory) {
                    t.setDefaultForCategory(its.defaultForCategory);
                    dirty = true;
                }
                if (dirty) {
                    t = itemTypeRepo.save(t);
                }

                itemTypesByName.put(its.name.toLowerCase(), t);
            }

            // 4) Role + Admin owner
            Role ownerRole = roleRepo.findByNameIgnoreCase(data.owner.role)
                    .orElseGet(() -> roleRepo.save(new Role(data.owner.role.toUpperCase())));

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

            // 5) Tenant link (AdminUserProject)
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

            // 6) Currency
            Currency currency = currencyRepo.findByCodeIgnoreCase(data.tenant.currencyCode)
                    .orElseGet(() -> currencyRepo.findByCurrencyType("DOLLAR")
                            .orElseThrow(() -> new IllegalStateException("USD currency not found. Ensure CurrencySeeder ran.")));

            // 7) Products (tenant-scoped)
            List<Product> existing = productRepo.findByOwnerProject_Id(aup.getId());
            Set<String> existingSkus = new HashSet<>();
            Set<String> existingNames = new HashSet<>();
            for (Product p : existing) {
                if (p.getSku() != null) existingSkus.add(p.getSku().toUpperCase());
                if (p.getName() != null) existingNames.add(p.getName().toLowerCase());
            }

            int inserted = 0;

            for (ProductSeed ps : data.products) {
                String skuKey = ps.sku != null ? ps.sku.toUpperCase() : null;
                String nameKey = ps.name != null ? ps.name.toLowerCase() : null;

                if (skuKey != null && existingSkus.contains(skuKey)) continue;
                if (skuKey == null && nameKey != null && existingNames.contains(nameKey)) continue;

                ItemType itemType = itemTypesByName.get(ps.itemTypeName.toLowerCase());
                if (itemType == null) {
                    throw new IllegalStateException("Missing itemType for product: " + ps.name + " -> " + ps.itemTypeName);
                }

                Product p = new Product();
                p.setOwnerProject(aup);
                p.setItemType(itemType);
                p.setItemName(ps.name);
                p.setDescription(ps.description);
                p.setPrice(ps.price != null ? ps.price : BigDecimal.ZERO);
                p.setCurrency(currency);
                p.setStock(ps.stock != null ? ps.stock : 0);
                p.setStatus("Active");
                p.setImageUrl(ps.imageUrl);
                p.setSku(ps.sku);
                p.setProductType(ps.productType != null ? ProductType.valueOf(ps.productType) : ProductType.SIMPLE);
                p.setVirtualProduct(false);
                p.setDownloadable(false);

                productRepo.save(p);
                inserted++;
                System.out.println("   • inserted Product: " + ps.name + " (sku=" + ps.sku + ")");
            }

            System.out.println("✅ NeroliGlow seeding complete. Inserted products: " + inserted);
            System.out.println("   Tenant (aup_id) = " + aup.getId() + ", slug=" + aup.getSlug());
            System.out.println("   Owner login (admin): " + data.owner.email + " / " + data.owner.password);
        };
    }
}
