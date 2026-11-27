package com.foodshare.medishare;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediAuditRepository extends JpaRepository<MediAudit, Long> {
    List<MediAudit> findTop200ByOrderByCreatedAtDesc();
}
