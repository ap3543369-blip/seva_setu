package com.foodshare.medishare;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"donation_id", "requester_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MedicineRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private MedicineDonation donation;

    @ManyToOne(optional = false)
    private User requester;

    private int quantity;
    @Column(length = 500)
    private String purpose;
    /** requester's NGO / hospital registration or licence number */
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediRequestStatus status = MediRequestStatus.PENDING;
    @Column(length = 500)
    private String decisionNote;

    private LocalDateTime createdAt = LocalDateTime.now();
}
