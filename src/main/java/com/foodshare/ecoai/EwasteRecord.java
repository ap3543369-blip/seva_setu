package com.foodshare.ecoai;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** E-waste collected by a recycler in one month for one category. This is the history the forecast learns from. */
@Entity
@Table(name = "ewaste_records", uniqueConstraints = @UniqueConstraint(columnNames = {"recycler_id", "month_key", "category"}))
@Getter
@Setter
@NoArgsConstructor
public class EwasteRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User recycler;

    /** YYYY-MM */
    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(nullable = false)
    private String category;

    private double kg;

    /** true = generated demo data, shown as such in the UI */
    private boolean seeded;
    private LocalDateTime createdAt = LocalDateTime.now();
}
