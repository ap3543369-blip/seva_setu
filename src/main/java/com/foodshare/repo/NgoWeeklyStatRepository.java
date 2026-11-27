package com.foodshare.repo;

import com.foodshare.model.NgoWeeklyStat;
import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NgoWeeklyStatRepository extends JpaRepository<NgoWeeklyStat, Long> {
    List<NgoWeeklyStat> findByNgoAndMonthKey(User ngo, String monthKey);

    Optional<NgoWeeklyStat> findByNgoAndMonthKeyAndWeekNo(User ngo, String monthKey, int weekNo);
}
