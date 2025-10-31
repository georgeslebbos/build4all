// src/main/java/com/build4all/business/domain/BusinessStatus.java
package com.build4all.business.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "business_status", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class BusinessStatus {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32, unique = true)
    private String name; // e.g. ACTIVE, INACTIVE, SUSPENDED

    public BusinessStatus() {}
    public BusinessStatus(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}
