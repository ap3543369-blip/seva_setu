package com.foodshare.repo;

import com.foodshare.model.ImpactSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImpactSnapshotRepository extends JpaRepository<ImpactSnapshot, Long> {
    Optional<ImpactSnapshot> findByRangeKey(String rangeKey);
}
