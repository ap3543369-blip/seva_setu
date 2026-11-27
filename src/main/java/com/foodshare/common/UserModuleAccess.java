package com.foodshare.common;

import com.foodshare.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-user override of the role default: allowed=true grants a module, allowed=false revokes it. */
@Entity
@Table(name = "user_module_access", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "module_key"}))
@Getter
@Setter
@NoArgsConstructor
public class UserModuleAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_key", nullable = false, length = 30)
    private PlatformModule module;

    private boolean allowed;
}
