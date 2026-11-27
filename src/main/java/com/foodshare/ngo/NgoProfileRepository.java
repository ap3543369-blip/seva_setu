package com.foodshare.ngo;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NgoProfileRepository extends JpaRepository<NgoProfile, Long> {
    Optional<NgoProfile> findByUser(User user);

    List<NgoProfile> findAllByOrderBySubmittedAtDesc();

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    long countByStatus(NgoApplicationStatus status);
}
