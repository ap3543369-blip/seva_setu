package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Column(nullable = false, length = 300)
    private String message;

    /** true once the user has opened the notification list. */
    private boolean seen;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification(User user, String message) {
        this.user = user;
        this.message = message;
    }
}
