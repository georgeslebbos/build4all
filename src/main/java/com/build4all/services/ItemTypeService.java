package com.build4all.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.build4all.entities.ItemType;
import com.build4all.entities.Interests;
import com.build4all.entities.Project;
import com.build4all.enums.ItemTypeEnum;
import com.build4all.enums.InterestEnum;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.repositories.InterestsRepository;
import com.build4all.repositories.ProjectRepository;

@Service
public class ItemTypeService {

    @Autowired private ItemTypeRepository itemTypeRepository;
    @Autowired private InterestsRepository interestsRepository;
    @Autowired private ProjectRepository projectRepository;

    // Configure which project to attach the seeded ItemTypes to.
    // Override in application.yml: app.default-project-id: 1
    @Value("${app.default-project-id:1}")
    private Long defaultProjectId;

    public void ensureItemTypes() {

        // 1) Ensure Interests exist
        Map<InterestEnum, Interests> interestMap = new HashMap<>();
        for (InterestEnum interestEnum : InterestEnum.values()) {
            Interests interest = interestsRepository.findByName(interestEnum.name())
                .orElseGet(() -> interestsRepository.save(new Interests(interestEnum.name())));
            interestMap.put(interestEnum, interest);
        }

        // 2) Get Project by **id** (not by name). If missing, create a default one.
        Project targetProject = projectRepository.findById(defaultProjectId)
            .orElseGet(() -> {
                Project p = new Project();
                p.setProjectName("Default Project");
                p.setDescription("Auto-created for seeding ItemTypes.");
                p.setActive(true);
                return projectRepository.save(p);
            });

        // 3) Seed ItemTypes (DB name kept equal to enum name to keep mappings stable)
        for (ItemTypeEnum typeEnum : ItemTypeEnum.values()) {
            String name = typeEnum.name(); // no spaces
            if (!itemTypeRepository.existsByName(name)) {
                ItemType type = new ItemType();
                type.setName(name);
                type.setInterest(interestMap.get(typeEnum.getInterest()));
                type.setProject(targetProject);                  // <-- link by project entity (id comes from DB)
                itemTypeRepository.save(type);
            } else {
                // Backfill project if null
                itemTypeRepository.findByName(name).ifPresent(existing -> {
                    if (existing.getProject() == null) {
                        existing.setProject(targetProject);
                        itemTypeRepository.save(existing);
                    }
                });
            }
        }
    }

    // Optional: if you want to call with a specific project id at runtime.
    public void ensureItemTypes(Long projectId) {
        this.defaultProjectId = projectId;
        ensureItemTypes();
    }
}
