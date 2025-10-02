package com.build4all.entities;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.build4all.enums.ItemIconEnum;

import com.build4all.enums.IconLibraryEnum;

import jakarta.persistence.*;

@Entity
@Table(name = "ItemTypes")
public class ItemType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_type_name")
    @JsonProperty("item_type")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_name")
    private ItemIconEnum icon;


    @Enumerated(EnumType.STRING)
    @Column(name = "icon_library")
    private IconLibraryEnum iconLib;
      
    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "interest_id", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Interests interest;

    // --- Constructors ---
    public ItemType() {}

    public ItemType(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public ItemType(Long id, String name, Interests interest) {
        this.id = id;
        this.name = name;
        this.interest = interest;
    }

    public ItemType(String name, Interests interest) {
        this.name = name;
        this.interest = interest;
    }

    public ItemType(String name, ItemIconEnum icon, IconLibraryEnum iconLib, Interests interest) {
        this.name = name;
        this.icon = icon;
        this.iconLib = iconLib;
        this.interest = interest;
    }

    public ItemIconEnum getIcon() { return icon; }
    public void setIcon(ItemIconEnum icon) { this.icon = icon; }


    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

 

    public IconLibraryEnum getIconLib() { return iconLib; }
    public void setIconLib(IconLibraryEnum iconLib) { this.iconLib = iconLib; }
    
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public Interests getInterest() { return interest; }
    public void setInterest(Interests interest) { this.interest = interest; }
}
