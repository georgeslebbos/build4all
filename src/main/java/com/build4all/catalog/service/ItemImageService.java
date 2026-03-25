package com.build4all.catalog.service;

import com.build4all.catalog.domain.Item;
import com.build4all.catalog.domain.ItemImage;
import com.build4all.catalog.dto.ItemImageDTO;
import com.build4all.catalog.repository.ItemImageRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ItemImageService {

    private final ItemImageRepository itemImageRepository;
    private final Path uploadRoot = Paths.get("uploads", "items");

    public ItemImageService(ItemImageRepository itemImageRepository) {
        this.itemImageRepository = itemImageRepository;

        try {
            if (!Files.exists(uploadRoot)) {
                Files.createDirectories(uploadRoot);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize item image upload directory", e);
        }
    }

    public List<ItemImageDTO> getImages(Long itemId) {
        return itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(itemId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<ItemImage> saveNewImages(Item item, List<MultipartFile> files, Integer mainImageIndex) throws IOException {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("Item must be saved before adding images");
        }

        if (files == null || files.isEmpty()) {
            syncMainImageField(item);
            return itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());
        }

        List<MultipartFile> cleanFiles = files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .collect(Collectors.toList());

        if (cleanFiles.isEmpty()) {
            syncMainImageField(item);
            return itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());
        }

        int startOrder = (int) itemImageRepository.countByItem_Id(item.getId());
        boolean itemHasMainAlready = itemImageRepository.findByItem_IdAndMainImageTrue(item.getId()).isPresent();

        if (mainImageIndex != null && (mainImageIndex < 0 || mainImageIndex >= cleanFiles.size())) {
            throw new IllegalArgumentException("mainImageIndex is out of range");
        }

        List<ItemImage> savedImages = new ArrayList<>();

        for (int i = 0; i < cleanFiles.size(); i++) {
            MultipartFile file = cleanFiles.get(i);

            ItemImage image = new ItemImage();
            image.setItem(item);
            image.setImageUrl(storeImage(file));
            image.setSortOrder(startOrder + i);

            boolean shouldBeMain;
            if (mainImageIndex != null) {
                shouldBeMain = (i == mainImageIndex);
            } else {
                shouldBeMain = !itemHasMainAlready && i == 0;
            }

            image.setMainImage(shouldBeMain);
            savedImages.add(itemImageRepository.save(image));
        }

        if (mainImageIndex != null) {
            Long forcedMainId = savedImages.get(mainImageIndex).getId();
            setMainImage(item, forcedMainId);
        } else {
            syncMainImageField(item);
        }

        return itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());
    }

    public void setMainImage(Item item, Long imageId) {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("Item is required");
        }
        if (imageId == null) {
            throw new IllegalArgumentException("imageId is required");
        }

        ItemImage target = itemImageRepository.findByIdAndItem_Id(imageId, item.getId())
                .orElseThrow(() -> new IllegalArgumentException("Image not found for this item"));

        List<ItemImage> images = itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());

        for (ItemImage image : images) {
            boolean isTarget = image.getId().equals(target.getId());
            image.setMainImage(isTarget);
        }

        itemImageRepository.saveAll(images);
        item.setImageUrl(target.getImageUrl());
    }

    public void removeImages(Item item, List<Long> imageIds) {
        if (item == null || item.getId() == null || imageIds == null || imageIds.isEmpty()) {
            return;
        }

        List<ItemImage> existing = itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());

        List<ItemImage> toDelete = existing.stream()
                .filter(img -> imageIds.contains(img.getId()))
                .collect(Collectors.toList());

        if (toDelete.isEmpty()) {
            return;
        }

        boolean deletedMain = toDelete.stream().anyMatch(ItemImage::isMainImage);

        for (ItemImage image : toDelete) {
            itemImageRepository.delete(image);
            deleteLocalImageIfManaged(image.getImageUrl());
        }

        List<ItemImage> remaining = itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());

        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setSortOrder(i);
        }

        if (deletedMain && !remaining.isEmpty()) {
            for (ItemImage image : remaining) {
                image.setMainImage(false);
            }
            remaining.get(0).setMainImage(true);
        }

        itemImageRepository.saveAll(remaining);
        syncMainImageField(item);
    }

    public void replaceAllImages(Item item, List<MultipartFile> files, Integer mainImageIndex) throws IOException {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("Item must be saved before replacing images");
        }

        List<ItemImage> oldImages = itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());

        for (ItemImage old : oldImages) {
            itemImageRepository.delete(old);
            deleteLocalImageIfManaged(old.getImageUrl());
        }

        item.setImageUrl(null);

        saveNewImages(item, files, mainImageIndex);
    }

    public void syncMainImageField(Item item) {
        if (item == null || item.getId() == null) {
            return;
        }

        List<ItemImage> images = itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());

        if (images.isEmpty()) {
            item.setImageUrl(null);
            return;
        }

        ItemImage main = images.stream()
                .filter(ItemImage::isMainImage)
                .findFirst()
                .orElseGet(() -> {
                    ItemImage first = images.stream()
                            .min(Comparator.comparing(ItemImage::getSortOrder).thenComparing(ItemImage::getId))
                            .orElse(null);

                    if (first != null) {
                        first.setMainImage(true);
                        itemImageRepository.save(first);
                    }
                    return first;
                });

        item.setImageUrl(main != null ? main.getImageUrl() : null);
    }
    
    
    public void deleteAllImages(Item item) {
        if (item == null || item.getId() == null) {
            return;
        }

        List<ItemImage> existing = itemImageRepository.findByItem_IdOrderBySortOrderAscIdAsc(item.getId());

        for (ItemImage image : existing) {
            itemImageRepository.delete(image);
            deleteLocalImageIfManaged(image.getImageUrl());
        }

        item.setImageUrl(null);
    }

    public ItemImageDTO toDto(ItemImage image) {
        if (image == null) return null;

        return new ItemImageDTO(
                image.getId(),
                image.getImageUrl(),
                image.getSortOrder(),
                image.isMainImage()
        );
    }

    private String storeImage(MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "image" : file.getOriginalFilename()
        );

        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }

        String fileName = UUID.randomUUID() + ext;
        Path target = uploadRoot.resolve(fileName);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/items/" + fileName;
    }

    private void deleteLocalImageIfManaged(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.startsWith("/uploads/")) return;

        try {
            String relative = url.substring("/uploads/".length()).replace("\\", "/");
            if (relative.isBlank()) return;

            Path filePath = Paths.get("uploads").resolve(relative).normalize();
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {
        }
    }
}