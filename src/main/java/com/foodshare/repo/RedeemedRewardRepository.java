package com.foodshare.repo;

import com.foodshare.model.RedeemedReward;
import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RedeemedRewardRepository extends JpaRepository<RedeemedReward, Long> {
    List<RedeemedReward> findByUserOrderByRedeemedAtDesc(User user);

    long countByUser(User user);

    void deleteByUserAndSeededFalse(User user);
}
