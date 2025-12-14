package com.build4all.user.domain;

import com.build4all.catalog.domain.Category;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "UserCategories")
public class UserCategories {

    /**
     * This entity represents a JOIN TABLE between:
     *   - Users
     *   - Category
     *
     * Meaning: a User can have many Categories, and a Category can be linked to many Users.
     *
     * Instead of using a simple @ManyToMany, you created a real entity so you can add columns like:
     *   created_at, updated_at
     */

    /* =========================================================
     * 1) Composite Primary Key (user_id + category_id)
     * =========================================================
     *
     * @Embeddable means this class is not a table by itself.
     * It is embedded as the ID of the UserCategories row.
     */
    @Embeddable
    public static class UserCategoryId implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Part 1 of the composite key: user_id
         * Many join rows can reference the same user.
         * @OnDelete(CASCADE): when a user is deleted -> delete join rows automatically.
         */
        @ManyToOne
        @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private Users user;

        /**
         * Part 2 of the composite key: category_id
         * Many join rows can reference the same category.
         */
        @ManyToOne
        @JoinColumn(name = "category_id", referencedColumnName = "category_id", nullable = false)
        private Category category;

        public UserCategoryId() {}

        public UserCategoryId(Users user, Category category) {
            this.user = user;
            this.category = category;
        }

        public Users getUser() { return user; }
        public void setUser(Users user) { this.user = user; }

        public Category getCategory() { return category; }
        public void setCategory(Category category) { this.category = category; }

        /**
         * equals/hashCode are REQUIRED for composite keys.
         * They must depend only on the key fields (user + category).
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserCategoryId that = (UserCategoryId) o;
            return Objects.equals(user, that.user) &&
                    Objects.equals(category, that.category);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, category);
        }
    }

    /* =========================================================
     * 2) The ID of this join entity
     * ========================================================= */
    @EmbeddedId
    private UserCategoryId id;

    /**
     * This is an explicit relation to Category for easier access:
     * userCategory.getCategory().getName() etc.
     *
     * @MapsId("category") means: this category field maps to the SAME column that is inside the id.category.
     *
     * IMPORTANT:
     * - You already have category_id in the composite key (id.category).
     * - So here you set insertable=false, updatable=false to avoid "duplicate column mapping" errors.
     */
    @ManyToOne
    @MapsId("category")
    @JoinColumn(
            name = "category_id",
            referencedColumnName = "category_id",
            insertable = false,
            updatable = false
    )
    private Category category;

    /* =========================================================
     * 3) Audit columns
     * ========================================================= */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Runs automatically before INSERT.
     * Sets both createdAt and updatedAt.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    /**
     * Runs automatically before UPDATE.
     * Updates updatedAt only.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /* =========================================================
     * 4) Getters / setters
     * ========================================================= */
    public UserCategoryId getId() { return id; }
    public void setId(UserCategoryId id) { this.id = id; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
