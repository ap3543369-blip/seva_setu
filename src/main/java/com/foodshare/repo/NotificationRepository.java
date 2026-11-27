package com.foodshare.repo;

import com.foodshare.model.Notification;
import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop20ByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserAndSeenFalse(User user);

    long countByUserAndSeenFalse(User user);
}
