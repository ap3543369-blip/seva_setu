package com.foodshare.service;

import com.foodshare.dto.Dtos.NotificationDto;
import com.foodshare.dto.Dtos.NotificationsResponse;
import com.foodshare.model.Notification;
import com.foodshare.model.User;
import com.foodshare.repo.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Called by other services (inside their transaction) to tell a user something happened. */
    public void notify(User user, String message) {
        notifications.save(new Notification(user, message));
    }

    @Transactional(readOnly = true)
    public NotificationsResponse list(User user) {
        List<NotificationDto> items = notifications.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .map(n -> new NotificationDto(n.getId(), n.getMessage(), n.isSeen(), n.getCreatedAt())).toList();
        return new NotificationsResponse(notifications.countByUserAndSeenFalse(user), items);
    }

    @Transactional(readOnly = true)
    public long unread(User user) {
        return notifications.countByUserAndSeenFalse(user);
    }

    @Transactional
    public void markAllSeen(User user) {
        List<Notification> unseen = notifications.findByUserAndSeenFalse(user);
        unseen.forEach(n -> n.setSeen(true));
        notifications.saveAll(unseen);
    }
}
