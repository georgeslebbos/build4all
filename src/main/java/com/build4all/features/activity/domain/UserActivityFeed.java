package com.build4all.features.activity.domain;

import com.build4all.feedType.FeedTypeSeeder;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.build4all.catalog.domain.Item;
import com.build4all.user.domain.Users;

@Entity
@Table(name = "user_activity_feed")
public class UserActivityFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users user;

    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false) // was activity_id
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_type_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FeedTypeSeeder.FeedType feedType;

    @Column(name = "feed_datetime", updatable = false)
    private LocalDateTime feedDatetime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserActivityFeed() {}

    public UserActivityFeed(Users user, Item item, FeedTypeSeeder.FeedType feedType) {
        this.user = user;
        this.item = item;
        this.feedType = feedType;
    }

    @PrePersist
    protected void onCreate() {
        this.feedDatetime = LocalDateTime.now();
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public Item getItem() { return item; } // renamed
    public void setItem(Item item) { this.item = item; } // renamed

    public FeedTypeSeeder.FeedType getFeedType() { return feedType; }
    public void setFeedType(FeedTypeSeeder.FeedType feedType) { this.feedType = feedType; }

    public LocalDateTime getFeedDatetime() { return feedDatetime; }
    public void setFeedDatetime(LocalDateTime feedDatetime) { this.feedDatetime = feedDatetime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
