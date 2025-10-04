package com.build4all.entities;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "ItemTypes")
public class ItemType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_type_name", unique = true, nullable = false)
    @JsonProperty("item_type")
    private String name;

    @Column(name = "icon_name")
    private String icon;

    @Column(name = "icon_library")
    private String iconLibrary;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
  
    @ManyToOne
    @JoinColumn(name = "interest_id", nullable = true)
    private Interest interest;

    public ItemType() {}
    public ItemType(String name, String icon, String iconLibrary, Project project, Interest interest) {
        this.name = name;
        this.icon = icon;
        this.iconLibrary = iconLibrary;
        this.project = project;
        this.interest = interest;
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getIconLibrary() { return iconLibrary; }
    public void setIconLibrary(String iconLibrary) { this.iconLibrary = iconLibrary; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public Interest getInterest() { return interest; }
    public void setInterest(Interest interest) { this.interest = interest; }
}
