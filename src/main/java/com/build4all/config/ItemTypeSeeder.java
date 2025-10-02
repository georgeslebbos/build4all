package com.build4all.config;

import com.build4all.entities.Interests;
import com.build4all.entities.ItemType;
import com.build4all.entities.Project;
import com.build4all.enums.*;
import com.build4all.repositories.InterestsRepository;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.repositories.ProjectRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ItemTypeSeeder {

    private final ItemTypeRepository itemTypeRepository;
    private final InterestsRepository interestsRepository;
    private final ProjectRepository projectRepository;

    public ItemTypeSeeder(ItemTypeRepository itemTypeRepository,
                          InterestsRepository interestsRepository,
                          ProjectRepository projectRepository) {
        this.itemTypeRepository = itemTypeRepository;
        this.interestsRepository = interestsRepository;
        this.projectRepository = projectRepository;
    }

    private Project ensureDefaultProject() {
        return projectRepository.findByProjectNameIgnoreCase("Default Project")
            .orElseGet(() -> {
                Project p = new Project();
                p.setProjectName("Default Project");
                p.setDescription("Auto-created for item types");
                p.setActive(true);
                return projectRepository.save(p);
            });
    }

    private ItemIconEnum iconFor(ItemTypeEnum t) {
        return switch (t) {
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

    @PostConstruct
    @Transactional
    public void seedAndNormalize() {
        // 1) Ensure default project exists BEFORE touching item types
        Project defaultProject = ensureDefaultProject();

        // 2) Ensure interests exist
        for (InterestEnum ie : InterestEnum.values()) {
            interestsRepository.findByName(ie.name())
                .orElseGet(() -> interestsRepository.save(new Interests(ie.name())));
        }

        // 3) Seed missing item types (always attach project!)
        for (ItemTypeEnum e : ItemTypeEnum.values()) {
            String name = e.getDisplayName();
            if (itemTypeRepository.findByName(name).isEmpty()) {
                Interests interest = interestsRepository.findByName(e.getInterest().name())
                        .orElseThrow(); // should exist from step 2

                ItemType t = new ItemType();
                t.setName(name);
                t.setInterest(interest);
                t.setIcon(iconFor(e));
                t.setIconLib(IconLibraryEnum.Ionicons);
                t.setProject(defaultProject);                  // <-- important
                itemTypeRepository.save(t);
            }
        }

        // 4) Normalize existing rows (backfill project/icon/iconLib)
        for (ItemType t : itemTypeRepository.findAll()) {
            boolean changed = false;

            if (t.getProject() == null) {
                t.setProject(defaultProject);                  // <-- backfill
                changed = true;
            }

            if (t.getIconLib() != IconLibraryEnum.Ionicons) {
                t.setIconLib(IconLibraryEnum.Ionicons);
                changed = true;
            }

            if (t.getIcon() == null) {
                try {
                    ItemTypeEnum e = ItemTypeEnum.valueOf(
                        t.getName().toUpperCase().replace(" ", "_")
                    );
                    t.setIcon(iconFor(e));
                    changed = true;
                } catch (IllegalArgumentException ignored) {}
            }

            if (changed) itemTypeRepository.save(t);
        }
    }
}
