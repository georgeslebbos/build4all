package com.build4all.notifications.repository;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;
import com.build4all.notifications.domain.Notifications;
import com.build4all.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationsRepository extends JpaRepository<Notifications, Long> {

    // User notifications
    List<Notifications> findByUserOrderByCreatedAtDesc(Users user);
    List<Notifications> findByUserAndIsReadFalse(Users user);
    int countByUserAndIsReadFalse(Users user);

    // Admin notifications
    List<Notifications> findByAdminOrderByCreatedAtDesc(AdminUser admin);
    List<Notifications> findByAdminAndIsReadFalse(AdminUser admin);
    int countByAdminAndIsReadFalse(AdminUser admin);

    /**
     * Transitional business methods kept temporarily so old code still compiles.
     * We will remove them after service/controller cleanup.
     */
    List<Notifications> findByBusinessOrderByCreatedAtDesc(Businesses business);
    List<Notifications> findByBusinessAndIsReadFalse(Businesses business);
    int countByBusinessAndIsReadFalse(Businesses business);
}