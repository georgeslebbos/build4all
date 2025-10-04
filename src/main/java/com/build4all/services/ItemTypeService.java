package com.build4all.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.build4all.entities.ItemType;
import com.build4all.entities.Interest;
import com.build4all.entities.Project;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.repositories.InterestRepository;
import com.build4all.repositories.ProjectRepository;

import java.util.List;

@Service
public class ItemTypeService {

    @Autowired private ItemTypeRepository itemTypeRepository;
    @Autowired private InterestRepository interestRepository;
    @Autowired private ProjectRepository projectRepository;

    // Configure which project to attach ItemTypes to
    @Value("${app.default-project-id:1}")
    private Long defaultProjectId;

    /**
     * Ensure default Project exists and backfill missing project links.
     */
    public void normalizeItemTypes() {
        // Ensure default project exists
        Project targetProject = projectRepository.findById(defaultProjectId)
            .orElseGet(() -> {
                Project p = new Project();
                p.setProjectName("Default Project");
                p.setDescription("Auto-created for ItemTypes.");
                p.setActive(true);
                return projectRepository.save(p);
            });

        // Ensure all ItemTypes have a project assigned
        for (ItemType type : itemTypeRepository.findAll()) {
            if (type.getProject() == null) {
                type.setProject(targetProject);
                itemTypeRepository.save(type);
            }
        }
    }

    /**
     * Return all ItemTypes with their Interests and Project.
     */
    public List<ItemType> getAllItemTypes() {
        return itemTypeRepository.findAll();
    }

    /**
     * Create a new ItemType.
     */
    public ItemType createItemType(String name, String icon, String iconLib, Long interestId, Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        Interest interest = null;
        if (interestId != null) {
            interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new RuntimeException("Interest not found: " + interestId));
        }

        ItemType type = new ItemType();
        type.setName(name);
        type.setIcon(icon);
        type.setIconLibrary(iconLib);
        type.setInterest(interest);
        type.setProject(project);

        return itemTypeRepository.save(type);
    }

    /**
     * Delete an ItemType.
     */
    public void deleteItemType(Long id) {
        itemTypeRepository.deleteById(id);
    }
}
