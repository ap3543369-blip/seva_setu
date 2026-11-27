package com.foodshare.web;

import com.foodshare.dto.Dtos.NotificationsResponse;
import com.foodshare.service.AuthService;
import com.foodshare.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final AuthService auth;

    public NotificationController(NotificationService notifications, AuthService auth) {
        this.notifications = notifications;
        this.auth = auth;
    }

    @GetMapping
    public NotificationsResponse list(HttpSession session) {
        return notifications.list(auth.requireUser(session));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(HttpSession session) {
        return Map.of("unread", notifications.unread(auth.requireUser(session)));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(HttpSession session) {
        notifications.markAllSeen(auth.requireUser(session));
    }
}
