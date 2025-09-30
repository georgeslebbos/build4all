package com.build4all.config;

import com.build4all.entities.Interests;
import com.build4all.enums.IconLibraryEnum;
import com.build4all.enums.InterestEnum;
import com.build4all.enums.InterestIconEnum;
import com.build4all.repositories.InterestsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class InterestSeeder {

    private final InterestsRepository interestsRepository;

    public InterestSeeder(InterestsRepository interestsRepository) {
        this.interestsRepository = interestsRepository;
    }

    @PostConstruct
    public void seedInterests() {
        // Optional: one-time fix for existing rows that used other libraries
        interestsRepository.findAll().forEach(existing -> {
            if (existing.getIconLib() != IconLibraryEnum.Ionicons) {
                existing.setIconLib(IconLibraryEnum.Ionicons);
                // keep existing icon enum value; names already mapped to Ionicons in InterestIconEnum
                interestsRepository.save(existing);
                System.out.println("🔄 Updated iconLib to Ionicons for: " + existing.getName());
            }
        });

        // Seed any missing interests
        for (InterestEnum interestEnum : InterestEnum.values()) {
            String name = interestEnum.name();

            if (interestsRepository.findByName(name).isEmpty()) {
                InterestIconEnum iconEnum = mapToIconEnum(name);

                Interests interest = new Interests();
                interest.setName(name);
                interest.setIcon(iconEnum);                // Ionicons enum names
                interest.setIconLib(IconLibraryEnum.Ionicons); // ✅ always Ionicons

                interestsRepository.save(interest);
                System.out.println("✅ Inserted: " + name + " with icon " + iconEnum.name());
            }
        }
    }

    private InterestIconEnum mapToIconEnum(String name) {
        return switch (name) {
            case "SPORTS" -> InterestIconEnum.BASKETBALL;
            case "MUSIC" -> InterestIconEnum.MUSIC;
            case "ART" -> InterestIconEnum.ART;
            case "TECH" -> InterestIconEnum.TECH;
            case "FITNESS" -> InterestIconEnum.FITNESS;
            case "COOKING" -> InterestIconEnum.COOKING;
            case "TRAVEL" -> InterestIconEnum.TRAVEL;
            case "GAMING" -> InterestIconEnum.GAMING;
            case "THEATER" -> InterestIconEnum.THEATER;
            case "LANGUAGE" -> InterestIconEnum.LANGUAGE;
            case "PHOTOGRAPHY" -> InterestIconEnum.PHOTOGRAPHY;
            case "DIY" -> InterestIconEnum.DIY;
            case "BEAUTY" -> InterestIconEnum.BEAUTY;
            case "FINANCE" -> InterestIconEnum.FINANCE;
            default -> InterestIconEnum.OTHER;
        };
    }
}
