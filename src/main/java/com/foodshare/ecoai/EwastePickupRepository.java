package com.foodshare.ecoai;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EwastePickupRepository extends JpaRepository<EwastePickup, Long> {
    List<EwastePickup> findByDonorOrderByCreatedAtDesc(User donor);

    List<EwastePickup> findByRecyclerOrderByCreatedAtDesc(User recycler);

    List<EwastePickup> findByStatusOrderByCreatedAtDesc(PickupStatus status);

    List<EwastePickup> findAllByOrderByCreatedAtDesc();
}
