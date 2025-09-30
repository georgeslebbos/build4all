package com.build4all.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.build4all.entities.ItemType;
import com.build4all.entities.Interests;
import com.build4all.enums.ItemTypeEnum;
import com.build4all.enums.InterestEnum;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.repositories.InterestsRepository;

@Service
public class ItemTypeService {

    @Autowired
    private ItemTypeRepository itemTypeRepository;

    @Autowired
    private InterestsRepository interestsRepository;

    public void ensureItemTypes() {
        Map<InterestEnum, Interests> interestMap = new HashMap<>();

        for (InterestEnum interestEnum : InterestEnum.values()) {
            Interests interest = interestsRepository.findByName(interestEnum.name())
                .orElseGet(() -> interestsRepository.save(new Interests(interestEnum.name())));
            interestMap.put(interestEnum, interest);
        }

        for (ItemTypeEnum typeEnum : ItemTypeEnum.values()) {
            String name = typeEnum.name().replace("_", " ");
            if (!itemTypeRepository.existsByName(name)) {
                ItemType type = new ItemType();
                type.setName(name);
                type.setInterest(interestMap.get(typeEnum.getInterest()));
                itemTypeRepository.save(type);
            }
        }
    }
}
