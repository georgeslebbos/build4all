package com.build4all.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(
        name = "icons",
        uniqueConstraints = @UniqueConstraint(name="uk_icon_name", columnNames = "name")
)
public class Icon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(name = "icon_library")
    private String library;

    public Icon() {}
    public Icon(String name, String library) {
        this.name = name;
        this.library = library;
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLibrary() { return library; }
    public void setLibrary(String library) { this.library = library; }
}
