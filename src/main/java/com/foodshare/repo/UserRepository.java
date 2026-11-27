package com.foodshare.repo;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(com.foodshare.model.Role role);

    long countByRoleAndVerifiedFalse(com.foodshare.model.Role role);

    java.util.List<User> findByRoleOrderByNameAsc(com.foodshare.model.Role role);

    java.util.List<User> findAllByOrderByCreatedAtDesc();
}
