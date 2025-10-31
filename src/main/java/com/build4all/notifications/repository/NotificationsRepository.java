package com.build4all.notifications.repository;

import com.build4all.notifications.domain.Notifications;
import com.build4all.user.domain.Users;
import com.build4all.business.domain.Businesses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationsRepository extends JpaRepository<Notifications, Long> {

    //  Notifications for Users
    List<Notifications> findByUserOrderByCreatedAtDesc(Users user);
    List<Notifications> findByUserAndIsReadFalse(Users user);
    int countByUserAndIsReadFalse(Users user);

    //  Notifications for Businesses (you need these 👇)
    List<Notifications> findByBusinessOrderByCreatedAtDesc(Businesses business);
    List<Notifications> findByBusinessAndIsReadFalse(Businesses business);
    int countByBusinessAndIsReadFalse(Businesses business);
}
