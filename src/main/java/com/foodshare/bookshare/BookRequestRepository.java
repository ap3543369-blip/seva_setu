package com.foodshare.bookshare;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRequestRepository extends JpaRepository<BookRequest, Long> {
    List<BookRequest> findByRequesterOrderByCreatedAtDesc(User requester);

    List<BookRequest> findByListingOrderByCreatedAtDesc(BookListing listing);

    Optional<BookRequest> findByListingAndRequester(BookListing listing, User requester);

    long countByListingAndStatus(BookListing listing, BookRequestStatus status);
}
