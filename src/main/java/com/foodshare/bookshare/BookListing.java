package com.foodshare.bookshare;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A set of books a donor wants to give away. */
@Entity
@Table(name = "book_listings")
@Getter
@Setter
@NoArgsConstructor
public class BookListing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User donor;

    @Column(nullable = false)
    private String title;
    private String author;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String level;
    private String language;
    @Column(nullable = false)
    private String bookCondition;

    /** copies offered / copies not yet promised to a requester */
    private int quantity;
    private int quantityLeft;

    @Column(length = 1000)
    private String description;
    private String city;
    private String pincode;
    private String address;
    private String phone;

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "file_name")
    private List<String> imageFiles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookStatus status = BookStatus.AVAILABLE;

    private boolean seeded;

    /** admin approval; null = treated as approved */
    @Enumerated(EnumType.STRING)
    private com.foodshare.common.ApprovalStatus approval;

    @Column(length = 500)
    private String reviewNote;

    public boolean isApproved() {
        return approval == null || approval == com.foodshare.common.ApprovalStatus.APPROVED;
    }
    private LocalDateTime createdAt = LocalDateTime.now();
}
