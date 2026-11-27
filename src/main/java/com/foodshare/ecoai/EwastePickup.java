package com.foodshare.ecoai;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A donor asking a recycler to collect old electronics. */
@Entity
@Table(name = "ewaste_pickups")
@Getter
@Setter
@NoArgsConstructor
public class EwastePickup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User donor;

    /** set when a recycler accepts the pickup */
    @ManyToOne
    private User recycler;

    @Column(nullable = false)
    private String category;
    @Column(length = 500)
    private String description;
    private double estimatedKg;
    private Double actualKg;

    private String address;
    private String city;
    private String pincode;
    private String phone;
    private LocalDate preferredDate;
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PickupStatus status = PickupStatus.REQUESTED;

    private boolean seeded;
    private LocalDateTime createdAt = LocalDateTime.now();
}
