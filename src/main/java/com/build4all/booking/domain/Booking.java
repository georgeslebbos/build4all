package com.build4all.booking.domain;

import com.build4all.catalog.domain.Currency;
import com.build4all.user.domain.Users;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;

    // 🔗 (Optional) Track the user who booked this item
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private Users user;

    // 👇 Add this to avoid lazy collection serialization
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ItemBooking> itemBookings = new ArrayList<>();

    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate = LocalDateTime.now();

    @Column(name = "status")
    private String status = "Pending";

    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;
    
    @ManyToOne
    @JoinColumn(name = "currency_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Currency currency;

   
    // Add helper methods
    public void addItemBooking(ItemBooking itemBooking) {
        itemBookings.add(itemBooking);
        itemBooking.setBooking(this);
    }

    public void removeItemBooking(ItemBooking itemBooking) {
        itemBookings.remove(itemBooking);
        itemBooking.setBooking(null);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }
    
    
    public Currency getCurrency() {
		return currency;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public LocalDateTime getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDateTime bookingDate) { this.bookingDate = bookingDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        this.status = (status == null) ? null : status.trim().toUpperCase();
    }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public List<ItemBooking> getItemBookings() { return itemBookings; }
    public void setItemBookings(List<ItemBooking> itemBookings) { this.itemBookings = itemBookings; }
}
