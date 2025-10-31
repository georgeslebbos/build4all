package com.build4all.user.domain;

import com.build4all.catalog.domain.Category;
import com.build4all.user.domain.Users;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "UserCategories")
public class UserCategories {

    @Embeddable
    public static class UserCategoryId implements Serializable {

        private static final long serialVersionUID = 1L;

        @ManyToOne
        @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private Users user;

        @ManyToOne
        @JoinColumn(name = "category_id", referencedColumnName = "category_id", nullable = false)
        private Category category;

        public UserCategoryId() {}

        public UserCategoryId(Users user, Category category) {
            this.user = user;
            this.category = category;
        }

        public Users getUser() {
            return user;
        }

        public void setUser(Users user) {
            this.user = user;
        }

        public Category getCategory() {
            return category;
        }

        public void setCategory(Category category) {
            this.category = category;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserCategoryId that = (UserCategoryId) o;
            return Objects.equals(user, that.user) && Objects.equals(category, that.category);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, category);
        }
    }

    @EmbeddedId
    private UserCategoryId id;

    @ManyToOne
    @MapsId("category")
    @JoinColumn(name = "category_id", referencedColumnName = "category_id", insertable = false, updatable = false)
    private Category category;

    // ✅ Add created_at and updated_at as the last two columns
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

    public UserCategoryId getId() {
        return id;
    }

    public void setId(UserCategoryId id) {
        this.id = id;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
