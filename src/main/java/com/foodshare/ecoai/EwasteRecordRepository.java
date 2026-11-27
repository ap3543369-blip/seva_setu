package com.foodshare.ecoai;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EwasteRecordRepository extends JpaRepository<EwasteRecord, Long> {
    List<EwasteRecord> findByRecyclerOrderByMonthKeyDescCategoryAsc(User recycler);

    List<EwasteRecord> findAllByOrderByMonthKeyAsc();

    Optional<EwasteRecord> findByRecyclerAndMonthKeyAndCategory(User recycler, String monthKey, String category);
}
