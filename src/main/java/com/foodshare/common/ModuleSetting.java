package com.foodshare.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Global on/off switch for a module (admin controlled). One row per module. */
@Entity
@Table(name = "module_settings")
@Getter
@Setter
@NoArgsConstructor
public class ModuleSetting {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "module_key", length = 30)
    private PlatformModule module;

    private boolean enabled;

    /** admin approval of new donations; null = module default (on for FoodShare and BookShare) */
    private Boolean requireApproval;

    public ModuleSetting(PlatformModule module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }
}
