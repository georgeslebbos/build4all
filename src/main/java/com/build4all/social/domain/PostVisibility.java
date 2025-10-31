package com.build4all.social.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "post_visibility")
public class PostVisibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    public PostVisibility() {
        // No-arg constructor
    }

    public PostVisibility(String name)
    {
        this.name = name;
    }

    public PostVisibility(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    // ✅ Getter and Setter for id
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // ✅ Getter and Setter for name
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
