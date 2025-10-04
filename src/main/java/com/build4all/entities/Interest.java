package com.build4all.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "interests")
public class Interest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long interest_id;

    @Column(unique = true, nullable = false)
    private String name;

    private String icon;

    private String iconLibrary;

    // --- Constructors ---
    public Interest() {}
    public Interest(String name, String icon, String iconLibrary) {
        this.name = name;
        this.icon = icon;
        this.iconLibrary = iconLibrary;
    }

    // --- Getters & Setters ---
    public Long getId() { return interest_id; }
    public void setId(Long interest_id) { this.interest_id = interest_id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getIconLibrary() { return iconLibrary; }
    public void setIconLibrary(String iconLibrary) { this.iconLibrary = iconLibrary; }
}
