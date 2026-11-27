package com.foodshare.medishare;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Append-only trail of every MediShare action (who did what, when). */
@Entity
@Table(name = "medi_audit")
@Getter
@Setter
@NoArgsConstructor
public class MediAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;
    private String actorName;
    private String actorRole;
    @Column(nullable = false)
    private String action;
    private String targetType;
    private Long targetId;
    @Column(length = 500)
    private String detail;
    private LocalDateTime createdAt = LocalDateTime.now();
}
