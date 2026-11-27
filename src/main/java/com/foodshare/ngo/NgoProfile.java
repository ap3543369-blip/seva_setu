package com.foodshare.ngo;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** The details an NGO fills in when it registers. The admin checks them before the NGO can open any module. */
@Entity
@Table(name = "ngo_profiles")
@Getter
@Setter
@NoArgsConstructor
public class NgoProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private User user;

    @Column(nullable = false)
    private String registrationNumber;
    private String orgKind;
    private int yearEstablished;
    private String contactPerson;
    private String contactPhone;

    private String address;
    private String city;
    private String state;
    private String pincode;
    private String website;

    @Column(length = 500)
    private String focusAreas;
    @Column(length = 300)
    private String serviceAreas;
    private long beneficiariesServed;

    /** name of the uploaded certificate inside the PRIVATE document folder (never served publicly) */
    private String documentFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NgoApplicationStatus status = NgoApplicationStatus.SUBMITTED;
    @Column(length = 500)
    private String reviewNote;

    private LocalDateTime submittedAt = LocalDateTime.now();
    private LocalDateTime reviewedAt;
}
