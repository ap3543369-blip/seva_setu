package com.foodshare.medishare;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_donations")
@Getter
@Setter
@NoArgsConstructor
public class MedicineDonation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User donor;

    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String medicineName;
    private String strength;
    private String dosageForm;
    @Column(nullable = false)
    private String batchNumber;
    @Column(nullable = false)
    private LocalDate expiryDate;

    private int quantity;
    private int quantityLeft;
    private String unit;

    private boolean sealedOriginalPack;
    private String storage;
    /** donor's dispensing / registration number, visible only to the donor and admins */
    private String licenceNumber;
    @Column(length = 1000)
    private String notes;

    private String address;
    private String city;
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediStatus status = MediStatus.PENDING_REVIEW;
    @Column(length = 500)
    private String reviewNote;

    private boolean seeded;
    private LocalDateTime createdAt = LocalDateTime.now();
}
