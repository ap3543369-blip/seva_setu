package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** "Restaurant / Canteen", "Mess", ... (donors) or "NGO". */
    private String orgType;

    private String phone;

    /** Reward points balance (donors). */
    private int points;

    // ---- totals imported from the old system (demo accounts only, 0 for new sign-ups) ----
    private int legacyDonations;
    private int legacyMeals;
    private int legacyCo2;
    private int legacyRequests;
    private int legacyFulfilled;
    private int legacyMealsReceived;
    private int legacyPartners;

    /** Blocked accounts cannot log in (set by an admin). */
    private boolean blocked;

    /** NGOs must be verified by an admin before they can request food. */
    private boolean verified = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
