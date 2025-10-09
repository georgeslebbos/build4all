package com.build4all.settings.domain;

import com.build4all.catalog.domain.Currency;
import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "app_settings")
public class AppSettings {

    @Id
    private Long id = 1L; // Only one row will ever exist

    @OneToOne
    @JoinColumn(name = "currency_id", referencedColumnName = "currency_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Currency currency;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    public AppSettings() {}

    public AppSettings(Currency currency) {
        this.currency = currency;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
