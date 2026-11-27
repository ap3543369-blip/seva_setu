package com.foodshare.medishare;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicineDonationRepository extends JpaRepository<MedicineDonation, Long> {
    List<MedicineDonation> findByDonorOrderByCreatedAtDesc(User donor);

    List<MedicineDonation> findByStatusOrderByCreatedAtDesc(MediStatus status);

    List<MedicineDonation> findAllByOrderByCreatedAtDesc();
}
