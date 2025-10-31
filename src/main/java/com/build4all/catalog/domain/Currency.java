package com.build4all.catalog.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "currency",
        uniqueConstraints = {
                @UniqueConstraint(name="uk_currency_type", columnNames = "currency_type"),
                @UniqueConstraint(name="uk_currency_code", columnNames = "code")
        },
        indexes = @Index(name="idx_currency_code", columnList = "code")
)
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "currency_id")
    private Long id;

    @Column(name = "currency_type", nullable = false, unique = true, length = 20)
    private String currencyType;

    @Column(name = "code", nullable = false, length = 5) // 👈 Add this line
    private String code;

    @Column(name = "symbol", length = 5)
    private String symbol;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Currency() {}

    public Currency(String currencyType, String symbol, String code) {
        this.currencyType = currencyType;
        this.symbol = symbol;
        this.code = code;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCurrencyType() { return currencyType; }
    public void setCurrencyType(String currencyType) { this.currencyType = currencyType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
