package com.build4all.entities;

import jakarta.persistence.*;


@Entity
@Table(name = "feed_types")
public class FeedType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
   
    @Column(nullable = false, unique = true)
    private String name;  // Values like "Post", "Event", etc.
    
    public FeedType() {
    	
    }
    
    public FeedType(String name) {
        this.name = name;
    }
}
