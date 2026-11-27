package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "redeemed_rewards")
@Getter
@Setter
@NoArgsConstructor
public class RedeemedReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    private String brand;
    private String logo;
    private int amount;
    private int cost;

    @Column(nullable = false)
    private String code;

    private LocalDateTime redeemedAt = LocalDateTime.now();

    private boolean seeded;
}
