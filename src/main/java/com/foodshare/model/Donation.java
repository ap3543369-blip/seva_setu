package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "donations")
@Getter
@Setter
@NoArgsConstructor
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User donor;

    /** NGO that accepted / received the donation (null while pending). */
    @ManyToOne
    private User ngo;

    @Column(nullable = false)
    private String foodType;

    /** Cooked, Vegetables, Bakery, Fruits, Packed, Raw - used by the filter on the Request Food page. */
    @Column(nullable = false)
    private String category;

    private double quantity;
    private String unit;

    @Column(length = 1000)
    private String description;

    private String foodCondition;
    private LocalDate bestDate;
    private LocalTime bestTime;

    private String address;
    private String city;
    private String pincode;
    private String phone;
    private String handover;

    private LocalDate pickupDate;
    private LocalTime timeFrom;
    private LocalTime timeTo;

    @Column(length = 1000)
    private String notes;

    private int meals;
    private int points;
    private double distanceKm;

    /** Short area name shown on cards (defaults to the city). */
    private String area;

    /** Name of the bundled placeholder picture (assets/images/food/{imageKey}.svg). */
    private String imageKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "donation_images", joinColumns = @JoinColumn(name = "donation_id"))
    @Column(name = "file_name")
    private List<String> imageFiles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DonationStatus status = DonationStatus.PENDING;

    /** True for rows created by the demo seeder. */
    private boolean seeded;

    /** admin approval; null = created before approvals existed = treated as approved */
    @Enumerated(EnumType.STRING)
    private com.foodshare.common.ApprovalStatus approval;

    @Column(length = 500)
    private String reviewNote;

    public boolean isApproved() {
        return approval == null || approval == com.foodshare.common.ApprovalStatus.APPROVED;
    }

    private LocalDateTime createdAt = LocalDateTime.now();
}
