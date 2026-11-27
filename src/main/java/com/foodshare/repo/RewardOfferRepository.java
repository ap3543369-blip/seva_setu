package com.foodshare.repo;

import com.foodshare.model.RewardOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RewardOfferRepository extends JpaRepository<RewardOffer, Long> {
    List<RewardOffer> findAllByOrderByCostAscIdAsc();

    Optional<RewardOffer> findByCode(String code);
}
