package com.build4all.review.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.review.domain.Review;
import com.build4all.review.repository.ReviewRepository;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.review.dto.ReviewDTO;
import com.build4all.notifications.service.NotificationsService;
import com.build4all.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewService {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private UsersRepository userRepository;
    @Autowired private OrderItemRepository OrderItemRepository;
    @Autowired private NotificationsService notificationsService;
    @Autowired private AdminUsersRepository adminUsersRepository;
    @Autowired private JwtUtil jwtUtil;

    public List<Review> getAllReviews() {
        return reviewRepository.findAllByOrderByDateDesc();
    }

    public List<Review> getReviewsByItem(Long itemId) {
        return reviewRepository.findByItemIdOrderByDateDesc(itemId);
    }

    public List<Review> getReviewsByBusiness(Long businessId) {
        return reviewRepository.findByBusinessId(businessId);
    }

    public Review createReviewFromDTO(ReviewDTO dto, String token) {
        String jwt = token.substring(7);
        String identifier = jwtUtil.extractUsername(jwt);
        Users user = userRepository.findByEmail(identifier);
        if (user == null) {
            user = userRepository.findByPhoneNumber(identifier);
        }
        if (user == null) throw new RuntimeException("User not found");

        Item item = itemRepository.findByIdWithBusiness(dto.getItemId())
                .orElseThrow(() -> new RuntimeException("Item not found"));

        boolean hasCompletedorder = OrderItemRepository
                .existsByItemIdAndUserIdAndOrderStatusName(item.getId(), user.getId(), "Completed");
       
        boolean alreadyReviewed = reviewRepository
                .existsByItemIdAndCustomerId(item.getId(), user.getId());

        if (!hasCompletedorder) {
            throw new RuntimeException("You can only review this item after completing a order.");
        }
        if (alreadyReviewed) {
            throw new RuntimeException("You already reviewed this item.");
        }

        Review review = new Review();
        review.setCustomer(user);
        review.setItem(item);
        review.setBusiness(item.getBusiness());

        if (dto.getRating() != null) {
            review.setRating(dto.getRating());
        }
        if (dto.getFeedback() != null && !dto.getFeedback().trim().isEmpty()) {
            review.setFeedback(dto.getFeedback().trim());
        }
        review.setDate(LocalDateTime.now());

        Review savedReview = reviewRepository.save(review);
        notificationsService.notifyBusiness(item.getBusiness(),
                user.getFirstName() + " reviewed your item: " + item.getItemName(),
                "NEW_REVIEW");
        return savedReview;
    }

    public boolean hasUserCompletedItem(Long itemId, String token) {
        String jwt = token.substring(7);
        String identifier = jwtUtil.extractUsername(jwt);
        Users user = userRepository.findByEmail(identifier);
        if (user == null) user = userRepository.findByPhoneNumber(identifier);
        if (user == null) throw new RuntimeException("User not found");

        return OrderItemRepository.existsByItemIdAndUserIdAndOrderStatusName(itemId, user.getId(), "Completed");
    }

    public List<Long> getCompletedItemIdsForUser(String token) {
        String jwt = token.substring(7);
        String email = jwtUtil.extractUsername(jwt);
        Users user = userRepository.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found");
        return OrderItemRepository.findCompletedItemIdsByUser(user.getId());
    }

    public boolean shouldShowReviewModal(Long itemId, String token) {
        String jwt = token.substring(7);
        String identifier = jwtUtil.extractUsername(jwt);
        Users user = userRepository.findByEmail(identifier);
        if (user == null) user = userRepository.findByPhoneNumber(identifier);
        if (user == null) throw new RuntimeException("User not found");

        boolean completed = OrderItemRepository
                .existsByItemIdAndUserIdAndOrderStatusName(itemId, user.getId(), "Completed");
        boolean alreadyReviewed = reviewRepository.existsByItemIdAndCustomerId(itemId, user.getId());

        return completed && !alreadyReviewed;
    }

    public Long getFirstCompletedUnreviewedItem(String token) {
        String jwt = token.substring(7);
        String identifier = jwtUtil.extractUsername(jwt);
        Users user = userRepository.findByEmail(identifier);
        if (user == null) user = userRepository.findByPhoneNumber(identifier);
        if (user == null) throw new RuntimeException("User not found");

        List<Long> completedItemIds = OrderItemRepository.findCompletedItemIdsByUser(user.getId());
        for (Long itemId : completedItemIds) {
            boolean alreadyReviewed = reviewRepository.existsByItemIdAndCustomerId(itemId, user.getId());
            if (!alreadyReviewed) return itemId;
        }
        return null;
    }
    
    public double checkAndNotifyIfLowRating(Long businessId) {
        // 1) load all reviews for the business
        List<Review> reviews = reviewRepository.findByBusinessId(businessId);
        if (reviews == null || reviews.isEmpty()) {
            // no reviews -> return a sentinel (your controller can interpret it)
            return -1d;
        }

        // 2) compute average rating (skip nulls just in case)
        double avg = reviews.stream()
                .map(Review::getRating)              // Integer or int
                .filter(r -> r != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        // 3) notify SUPER_ADMINs if low (<= 3.0) and they opted in
        if (avg <= 3.0) {
            List<AdminUser> adminsToNotify = adminUsersRepository.findAll().stream()
                    .filter(a -> a.getRole() != null
                              && "SUPER_ADMIN".equalsIgnoreCase(a.getRole().getName())
                              && Boolean.TRUE.equals(a.getNotifyUserFeedback()))
                    .toList();

            for (AdminUser admin : adminsToNotify) {
                notificationsService.notifyAdmin(
                    admin,
                    "Alert: Business " + businessId + " average rating is " + avg
                    + ". Consider reviewing its status.",
                    "NEW_REVIEW"
                );
            }
        }

        return avg;
    }
}