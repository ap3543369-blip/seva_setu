package com.foodshare.repo;

import com.foodshare.model.Donation;
import com.foodshare.model.DonationStatus;
import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByDonorOrderByCreatedAtDesc(User donor);

    List<Donation> findByStatusOrderByCreatedAtDesc(DonationStatus status);

    long countByDonor(User donor);

    long countByDonorAndStatusNot(User donor, DonationStatus status);

    long countByStatus(DonationStatus status);

    List<Donation> findAllByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(d.meals), 0L) from Donation d where d.status <> com.foodshare.model.DonationStatus.CANCELLED")
    long sumAllMeals();

    @Query("select coalesce(sum(d.meals), 0L) from Donation d where d.donor = :donor and d.status <> com.foodshare.model.DonationStatus.CANCELLED")
    long sumMealsByDonor(@Param("donor") User donor);

    /** Meals from donations made by real users (not demo seed data) since the given moment. */
    @Query("select coalesce(sum(d.meals), 0L) from Donation d where d.seeded = false and d.createdAt >= :since")
    long sumLiveMealsSince(@Param("since") LocalDateTime since);
}
