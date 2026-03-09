package com.build4all.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "item_statuses")
public class ItemStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public ItemStatus() {}

    public ItemStatus(String code, String name, Boolean active, Integer sortOrder) {
        this.code = code;
        this.name = name;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Boolean getActive() {
        return active;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}