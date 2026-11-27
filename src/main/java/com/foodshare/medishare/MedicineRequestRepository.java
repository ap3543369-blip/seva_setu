package com.foodshare.medishare;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicineRequestRepository extends JpaRepository<MedicineRequest, Long> {
    List<MedicineRequest> findByRequesterOrderByCreatedAtDesc(User requester);

    List<MedicineRequest> findByDonationOrderByCreatedAtDesc(MedicineDonation donation);

    Optional<MedicineRequest> findByDonationAndRequester(MedicineDonation donation, User requester);

    long countByDonationAndStatus(MedicineDonation donation, MediRequestStatus status);
}
