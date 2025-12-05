package com.build4all.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "countries")
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "country_id")
    private Long id;

    @Column(name = "iso2_code", length = 2, nullable = false, unique = true)
    private String iso2Code;   // "LB", "SA", ...

    @Column(name = "iso3_code", length = 3, unique = true)
    private String iso3Code;   // "LBN", "SAU" (optional)

    @Column(name = "name", nullable = false)
    private String name;       // "Lebanon", "Saudi Arabia"

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // --- getters & setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIso2Code() { return iso2Code; }
    public void setIso2Code(String iso2Code) { this.iso2Code = iso2Code; }

    public String getIso3Code() { return iso3Code; }
    public void setIso3Code(String iso3Code) { this.iso3Code = iso3Code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}