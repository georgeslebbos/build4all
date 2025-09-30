package com.build4all.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "UserInterests")
public class UserInterests {

    @Embeddable
    public static class UserInterestId implements Serializable {

        private static final long serialVersionUID = 1L;

        @ManyToOne
        @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
        @OnDelete(action = OnDeleteAction.CASCADE)
        private Users user;

        @ManyToOne
        @JoinColumn(name = "interest_id", referencedColumnName = "interest_id", nullable = false)
        private Interests interest;

        public UserInterestId() {}

        public UserInterestId(Users user, Interests interest) {
            this.user = user;
            this.interest = interest;
        }

        public Users getUser() {
            return user;
        }

        public void setUser(Users user) {
            this.user = user;
        }

        public Interests getInterest() {
            return interest;
        }

        public void setInterest(Interests interest) {
            this.interest = interest;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserInterestId that = (UserInterestId) o;
            return Objects.equals(user, that.user) && Objects.equals(interest, that.interest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, interest);
        }
    }

    @EmbeddedId
    private UserInterestId id;

    @ManyToOne
    @MapsId("interest")
    @JoinColumn(name = "interest_id", referencedColumnName = "interest_id", insertable = false, updatable = false)
    private Interests interest;

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

    public UserInterestId getId() {
        return id;
    }

    public void setId(UserInterestId id) {
        this.id = id;
    }

    public Interests getInterest() {
        return interest;
    }

    public void setInterest(Interests interest) {
        this.interest = interest;
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
