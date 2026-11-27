package com.foodshare.common;

import com.foodshare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserModuleAccessRepository extends JpaRepository<UserModuleAccess, Long> {
    Optional<UserModuleAccess> findByUserAndModule(User user, PlatformModule module);

    List<UserModuleAccess> findByUser(User user);
}
