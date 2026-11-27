package com.foodshare.repo;

import com.foodshare.model.Donation;
import com.foodshare.model.FoodRequest;
import com.foodshare.model.RequestStatus;
import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRequestRepository extends JpaRepository<FoodRequest, Long> {

    List<FoodRequest> findByNgoOrderByCreatedAtDesc(User ngo);

    List<FoodRequest> findByDonation(Donation donation);

    List<FoodRequest> findByDonationAndStatus(Donation donation, RequestStatus status);

    Optional<FoodRequest> findByIdAndNgo(Long id, User ngo);

    boolean existsByDonationAndNgo(Donation donation, User ngo);

    long countByNgo(User ngo);

    long countByNgoAndStatus(User ngo, RequestStatus status);

    @Query("select count(distinct r.donation.donor.id) from FoodRequest r where r.ngo = :ngo")
    long countPartnerDonors(@Param("ngo") User ngo);

    @Query("select coalesce(sum(r.donation.meals), 0L) from FoodRequest r "
            + "where r.ngo = :ngo and r.status = com.foodshare.model.RequestStatus.DELIVERED")
    long sumDeliveredMeals(@Param("ngo") User ngo);

    @Query("select r.donation.id from FoodRequest r where r.ngo = :ngo")
    List<Long> findRequestedDonationIds(@Param("ngo") User ngo);
}
