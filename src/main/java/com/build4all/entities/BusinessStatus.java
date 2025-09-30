package com.build4all.entities;

import jakarta.persistence.*;


@Entity
@Table(name = "business_status")
public class BusinessStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "ACTIVE", "INACTIVE", "DELETED"
    
   public BusinessStatus() {
	   
   }
    
    public BusinessStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }


}
