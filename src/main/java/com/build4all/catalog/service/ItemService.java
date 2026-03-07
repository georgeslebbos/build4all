package com.build4all.catalog.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.business.domain.Businesses;
import com.build4all.business.service.BusinessService;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.GenericItem;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.domain.ItemStatus;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.dto.ItemPriceResponse;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.catalog.repository.ItemStatusRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ItemService {

    private static final String STATUS_DRAFT = "DRAFT";

    private final ItemRepository itemsRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final BusinessService businessService;
    private final AdminUserProjectRepository adminUserProjectRepository;
    private final ItemStatusRepository itemStatusRepository;

    public ItemService(ItemRepository itemsRepository,
                       ItemTypeRepository itemTypeRepository,
                       CurrencyRepository currencyRepository,
                       BusinessService businessService,
                       AdminUserProjectRepository adminUserProjectRepository,
                       ItemStatusRepository itemStatusRepository) {
        this.itemsRepository = itemsRepository;
        this.itemTypeRepository = itemTypeRepository;
        this.currencyRepository = currencyRepository;
        this.businessService = businessService;
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.itemStatusRepository = itemStatusRepository;
    }

    /* =========================================================
       STATUS HELPERS
       ========================================================= */

    private String normalizeStatusCode(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isBlank()) return null;
        return s.toUpperCase(Locale.ROOT);
    }

    private ItemStatus resolveStatusOrDefault(String rawStatusCode) {
        String normalized = normalizeStatusCode(rawStatusCode);
        String finalCode = (normalized == null) ? STATUS_DRAFT : normalized;

        return itemStatusRepository.findByCode(finalCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid statusCode: " + finalCode));
    }

    // -------------------- Create / Update --------------------

    public Item createItemWithImage(
            String itemName,
            Long itemTypeId,
            String description,
            BigDecimal price,
            String statusCode,
            Long businessId,
            Long ownerProjectId,
            MultipartFile image
    ) throws IOException {

        String imageUrl = storeImageIfPresent(image);

        Businesses business = businessService.findById(businessId);
        if (business == null) throw new IllegalArgumentException("Business not found");

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));

        AdminUserProject ownerProject = adminUserProjectRepository.findById(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Owner project (app) not found"));

        Currency currency = ownerProject.getCurrency();
        if (currency == null) {
            currency = currencyRepository.findByCurrencyType("CAD")
                    .orElseThrow(() -> new RuntimeException("Default currency not found"));
        }

        ItemStatus status = resolveStatusOrDefault(statusCode);

        Item item = new GenericItem();
        item.setItemName(itemName);
        item.setItemType(type);
        item.setDescription(description);
        item.setPrice(price);
        item.setStatus(status);
        item.setImageUrl(imageUrl);
        item.setBusiness(business);
        item.setOwnerProject(ownerProject);
        item.setCurrency(currency);

        return itemsRepository.save(item);
    }

    public Item updateItemWithImage(
            Long id,
            String itemName,
            Long itemTypeId,
            String description,
            BigDecimal price,
            String statusCode,
            Long businessId,
            MultipartFile image,
            boolean imageRemoved
    ) throws IOException {

        Item item = itemsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));

        item.setItemName(itemName);
        item.setItemType(type);
        item.setDescription(description);
        item.setPrice(price);

        if (statusCode != null && !statusCode.isBlank()) {
            item.setStatus(resolveStatusOrDefault(statusCode));
        }

        Businesses business = businessService.findById(businessId);
        if (business == null) throw new IllegalArgumentException("Business not found");
        item.setBusiness(business);

        if (imageRemoved && item.getImageUrl() != null) {
            tryDeleteExisting(item.getImageUrl());
            item.setImageUrl(null);
        }

        if (image != null && !image.isEmpty()) {
            String imageUrl = storeImageIfPresent(image);
            item.setImageUrl(imageUrl);
        }

        return itemsRepository.save(item);
    }

    // -------------------- Reads --------------------

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Item> findByBusinessId(Long businessId) {
        return itemsRepository.findByBusinessId(businessId);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Item findById(Long id) {
        return itemsRepository.findById(id).orElse(null);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Item findByIdWithBusiness(Long id) {
        return itemsRepository.findByIdWithBusiness(id).orElse(null);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Item> findAllVisibleItemsForUsers() {
        return itemsRepository.findAllPublicActiveBusinessItems();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Item> findByItemTypeId(Long typeId) {
        return itemsRepository.findByItemType_Id(typeId);
    }

    public Item save(Item item) {
        return itemsRepository.save(item);
    }

    public void deleteItem(Long id) {
        itemsRepository.deleteById(id);
    }

    public void deleteByBusiness(Long businessId) {
        itemsRepository.deleteByBusinessId(businessId);
    }

    // -------------------- Analytics / projections --------------------

    @Transactional(Transactional.TxType.SUPPORTS)
    public long countCreatedAfter(LocalDateTime date) {
        return itemsRepository.countByCreatedAtAfter(date);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public String getTopItemNameByBusiness(Long businessId) {
        return itemsRepository.findTopItemNameByBusinessId(businessId);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ItemPriceResponse> getItemsWithCurrencySymbol(Long ownerProjectId) {
        AdminUserProject app = adminUserProjectRepository.findById(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Owner project (app) not found"));

        Currency currency = app.getCurrency();
        if (currency == null) {
            throw new IllegalStateException("Currency not set for this app");
        }

        return itemsRepository.findByOwnerProjectId(ownerProjectId).stream()
                .map(it -> new ItemPriceResponse(
                        it.getId(),
                        it.getItemName(),
                        it.getPrice(),
                        currency.getSymbol()
                ))
                .toList();
    }

    // -------------------- File helpers --------------------

    private static String storeImageIfPresent(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) return null;

        String filename = UUID.randomUUID() + "_" + StringUtils.cleanPath(image.getOriginalFilename());
        Path uploadPath = Paths.get("uploads/");
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(filename);
        Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + filename;
    }

    private static void tryDeleteExisting(String existingUrl) {
        try {
            String justName = Paths.get(existingUrl).getFileName().toString();
            Path oldImagePath = Paths.get("uploads/", justName);
            Files.deleteIfExists(oldImagePath);
        } catch (Exception ignore) { }
    }
}