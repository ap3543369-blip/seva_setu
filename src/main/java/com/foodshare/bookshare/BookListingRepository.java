package com.foodshare.bookshare;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookListingRepository extends JpaRepository<BookListing, Long> {
    List<BookListing> findByDonorOrderByCreatedAtDesc(User donor);

    List<BookListing> findByStatusOrderByCreatedAtDesc(BookStatus status);

    List<BookListing> findAllByOrderByCreatedAtDesc();
}
