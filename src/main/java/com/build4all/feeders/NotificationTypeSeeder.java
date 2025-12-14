package com.build4all.feeders;

import com.build4all.notifications.domain.NotificationTypeEntity;
import com.build4all.notifications.repository.NotificationTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class NotificationTypeSeeder {

    @Bean
    public CommandLineRunner seedNotificationTypes(NotificationTypeRepository repository) {
        return args -> {
            System.out.println("✅ NotificationType Seeder running...");

            Map<String, String> types = Map.ofEntries(
                Map.entry("ACTIVITY_UPDATE", "Activity Update"),
                Map.entry("MESSAGE", "Message"),
                Map.entry("order_REMINDER", "order Reminder"),
                Map.entry("EVENT_REMINDER", "Event Reminder"),
                Map.entry("order_CREATED", "order Created"),
                Map.entry("order_CANCELLED", "order Cancelled"),
                Map.entry("order_PENDING", "order Returned to Pending"),
                Map.entry("NEW_REVIEW", "New Review"),
                Map.entry("FRIEND_REQUEST_SENT", "Friend Request Sent"),
                Map.entry("FRIEND_REQUEST_ACCEPTED", "Friend Request Accepted"),
                Map.entry("FRIEND_REQUEST_REJECTED", "Friend Request Rejected"),
                Map.entry("FRIEND_REMOVED", "Friend Removed"),
                Map.entry("FRIEND_REQUEST_CANCELLED", "Friend Request Cancelled"),
                Map.entry("FRIEND_BLOCKED", "Friend Blocked")
            );

            for (Map.Entry<String, String> entry : types.entrySet()) {
                String code = entry.getKey();
                String description = entry.getValue();

                boolean exists = repository.findByCodeIgnoreCase(code).isPresent();
                if (!exists) {
                    NotificationTypeEntity type = new NotificationTypeEntity();
                    type.setCode(code);
                    type.setDescription(description);
                    repository.save(type);
                    System.out.println("➕ Inserted NotificationType: " + code);
                }
            }
        };
    }
}
