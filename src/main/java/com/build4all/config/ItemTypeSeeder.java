package com.build4all.config;

import com.build4all.entities.Interests;
import com.build4all.entities.ItemType;
import com.build4all.enums.*;
import com.build4all.repositories.InterestsRepository;
import com.build4all.repositories.ItemTypeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ItemTypeSeeder {

    private final ItemTypeRepository itemTypeRepository;
    private final InterestsRepository interestsRepository;

    public ItemTypeSeeder(ItemTypeRepository itemTypeRepository,
                          InterestsRepository interestsRepository) {
        this.itemTypeRepository = itemTypeRepository;
        this.interestsRepository = interestsRepository;
    }

    @PostConstruct
    public void seedItemTypes() {
        for (ItemTypeEnum enumValue : ItemTypeEnum.values()) {
            String itemTypeName = enumValue.getDisplayName();

            if (itemTypeRepository.findByName(itemTypeName).isEmpty()) {

                String interestName = enumValue.getInterest().name();

                Interests interestEntity = interestsRepository.findByName(interestName)
                        .orElseGet(() -> interestsRepository.save(new Interests(interestName)));

                ItemIconEnum iconEnum = getIconForItem(enumValue);
                IconLibraryEnum iconLibEnum = getIconLibraryForItem(enumValue); // always Ionicons

                ItemType newType = new ItemType(
                        itemTypeName,
                        iconEnum,
                        iconLibEnum,
                        interestEntity
                );

                itemTypeRepository.save(newType);
            }
        }
    }

    private ItemIconEnum getIconForItem(ItemTypeEnum type) {
        return switch (type) {
            case HIKING -> ItemIconEnum.TREE;
            case FOOTBALL -> ItemIconEnum.FOOTBALL_BALL;
            case YOGA -> ItemIconEnum.SPA;
            case MUSIC, MUSIC_PRODUCTION, DANCE -> ItemIconEnum.MUSIC;
            case ART, SCULPTING, KNITTING, CALLIGRAPHY -> ItemIconEnum.PALETTE;
            case CODING, ROBOTICS -> ItemIconEnum.CODE;
            case COOKING -> ItemIconEnum.RESTAURANT;
            case GAMING, BOARD_GAMES -> ItemIconEnum.GAMEPAD;
            case WRITING, PUBLIC_SPEAKING -> ItemIconEnum.BOOK_OPEN;
            case PHOTOGRAPHY -> ItemIconEnum.CAMERA;
            case FILM_MAKING -> ItemIconEnum.VIDEO;
            case FITNESS, SELF_DEFENSE -> ItemIconEnum.DUMBBELL;
            case TRAVEL, NATURE_WALKS -> ItemIconEnum.GLOBE;
            case MAKEUP_BEAUTY -> ItemIconEnum.HEART;
            case THEATER, STAND_UP_COMEDY -> ItemIconEnum.THEATER_MASKS;
            default -> ItemIconEnum.STAR;
        };
    }

    // 🚨 Ionicons-only now
    private IconLibraryEnum getIconLibraryForItem(ItemTypeEnum type) {
        return IconLibraryEnum.Ionicons;
    }

    @PostConstruct
    public void updateExistingItemTypesWithIcons() {
        for (ItemType type : itemTypeRepository.findAll()) {
            boolean changed = false;

            // normalize icon enum if missing
            if (type.getIcon() == null) {
                try {
                    ItemTypeEnum typeEnum = ItemTypeEnum.valueOf(type.getName().toUpperCase().replace(" ", "_"));
                    type.setIcon(getIconForItem(typeEnum));
                    changed = true;
                } catch (IllegalArgumentException e) {
                    System.out.println("⚠️ No matching enum for: " + type.getName());
                }
            }

            // force Ionicons for everyone
            if (type.getIconLib() != IconLibraryEnum.Ionicons) {
                type.setIconLib(IconLibraryEnum.Ionicons);
                changed = true;
            }

            if (changed) {
                itemTypeRepository.save(type);
                System.out.println("✅ Normalized " + type.getName() + " to Ionicons.");
            }
        }
    }
}
