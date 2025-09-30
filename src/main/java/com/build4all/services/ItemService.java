package com.build4all.services;

import com.build4all.dto.ItemPriceResponse;
import com.build4all.entities.AppSettings;
import com.build4all.entities.Businesses;
import com.build4all.entities.Currency;
import com.build4all.entities.Item;
import com.build4all.entities.ItemType;
import com.build4all.repositories.AppSettingsRepository;
import com.build4all.repositories.CurrencyRepository;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.repositories.ItemRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ItemService {

    private final ItemRepository itemsRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final BusinessService businessService;
    private final AppSettingsRepository appSettingsRepository;

    public ItemService(ItemRepository itemsRepository,
                       ItemTypeRepository itemTypeRepository,
                       CurrencyRepository currencyRepository,
                       BusinessService businessService,
                       AppSettingsRepository appSettingsRepository) {
        this.itemsRepository = itemsRepository;
        this.itemTypeRepository = itemTypeRepository;
        this.currencyRepository = currencyRepository;
        this.businessService = businessService;
        this.appSettingsRepository = appSettingsRepository;
    }

    // -------------------- Create / Update (Item has only base fields) --------------------

    public Item createItemWithImage(
            String itemName,
            Long itemTypeId,
            String description,
            BigDecimal price,
            String status,        // e.g. "Upcoming", "Active", etc.
            Long businessId,
            MultipartFile image
    ) throws IOException {

        String imageUrl = storeImageIfPresent(image);

        Businesses business = businessService.findById(businessId);
        if (business == null) throw new IllegalArgumentException("Business not found");

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));

        // default currency fallback (keep as in your original code)
        Currency currency = currencyRepository.findByCurrencyType("CAD")
                .orElseThrow(() -> new RuntimeException("Default currency not found"));

        // Because Item is abstract, you will persist a concrete subclass elsewhere.
        // If you use Item directly via a concrete subclass, construct that subclass here instead.
        // Assuming you have a concrete mapped subclass like Product/Service/Activity later.
        // If not, make Item concrete (remove abstract) or use a factory outside.
       // Item item = new com.build4all.entities.Product(); // <-- replace with your concrete subclass
        Item item = new com.build4all.entities.GenericItem();
        item.setItemName(itemName);
        item.setItemType(type);
        item.setDescription(description);
        item.setPrice(price);
        item.setStatus(status != null ? status : "Upcoming");
        item.setImageUrl(imageUrl);
        item.setBusiness(business);
        item.setCurrency(currency);
        // stock is required (not null) per your entity; keep default 0 if not set elsewhere

        return itemsRepository.save(item);
    }

    public Item updateItemWithImage(
            Long id,
            String itemName,
            Long itemTypeId,
            String description,
            BigDecimal price,
            String status,
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
        if (status != null) item.setStatus(status);

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
        // Requires your repo method that filters by business ACTIVE + public profile
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

    // -------------------- Simple analytics / projections --------------------

    @Transactional(Transactional.TxType.SUPPORTS)
    public long countCreatedAfter(LocalDateTime date) {
        return itemsRepository.countByCreatedAtAfter(date);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public String getTopItemNameByBusiness(Long businessId) {
        return itemsRepository.findTopItemNameByBusinessId(businessId);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ItemPriceResponse> getItemsWithCurrencySymbol() {
        Currency selectedCurrency = appSettingsRepository.findById(1L)
                .map(AppSettings::getCurrency)
                .orElseThrow(() -> new RuntimeException("Currency not set in app settings"));

        return itemsRepository.findAll().stream()
                .map(it -> new ItemPriceResponse(
                        it.getId(),
                        it.getItemName(),
                        it.getPrice(),                    // price comes from Item
                        selectedCurrency.getSymbol()
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
    
 // inside ItemService
    public Item createActivityWithImage(
            String name,
            Long itemTypeId,
            String description,
            String location,
            Double latitude,
            Double longitude,
            int maxParticipants,
            BigDecimal price,
            LocalDateTime startDatetime,
            LocalDateTime endDatetime,
            String status,
            Long businessId,
            MultipartFile image
    ) throws IOException {

        String imageUrl = storeImageIfPresent(image);

        Businesses business = businessService.findById(businessId);
        if (business == null) throw new IllegalArgumentException("Business not found");

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));

        Currency currency = currencyRepository.findByCurrencyType("CAD")
                .orElseThrow(() -> new RuntimeException("Default currency not found"));

        // ⬇️ concrete subclass with activity fields
        com.build4all.entities.Activity activity = new com.build4all.entities.Activity();
        // base (Item) fields
        activity.setItemName(name);
        activity.setItemType(type);
        activity.setDescription(description);
        activity.setPrice(price);
        activity.setStatus((status != null && !status.isBlank()) ? status : "Upcoming");
        activity.setImageUrl(imageUrl);
        activity.setBusiness(business);
        activity.setCurrency(currency);
        // activity-specific fields
        activity.setLocation(location);
        activity.setLatitude(latitude);
        activity.setLongitude(longitude);
        activity.setMaxParticipants(maxParticipants);
        activity.setStartDatetime(startDatetime);
        activity.setEndDatetime(endDatetime);

        return itemsRepository.save(activity);
    }

}
