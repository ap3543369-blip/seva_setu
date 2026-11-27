package com.foodshare.common;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Makes sure every module has a settings row (new rows start switched ON; the admin can switch any module off). */
@Component
@org.springframework.core.annotation.Order(0)
public class ModuleInitializer implements CommandLineRunner {
    private final ModuleSettingRepository settings;

    public ModuleInitializer(ModuleSettingRepository settings) {
        this.settings = settings;
    }

    @Override
    public void run(String... args) {
        for (PlatformModule m : PlatformModule.values()) {
            if (!settings.existsById(m)) {
                settings.save(new ModuleSetting(m, true));
            }
        }
    }
}
