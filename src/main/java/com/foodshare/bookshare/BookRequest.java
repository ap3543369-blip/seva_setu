package com.foodshare.bookshare;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A recipient or NGO asking for copies of a listing. */
@Entity
@Table(name = "book_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"listing_id", "requester_id"}))
@Getter
@Setter
@NoArgsConstructor
public class BookRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private BookListing listing;

    @ManyToOne(optional = false)
    private User requester;

    private int quantity;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookRequestStatus status = BookRequestStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();
}
