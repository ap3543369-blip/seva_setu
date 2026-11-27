package com.foodshare.common;

import com.foodshare.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ModuleController {
    private final ModuleAccessService modules;
    private final AuthService auth;

    public ModuleController(ModuleAccessService modules, AuthService auth) {
        this.modules = modules;
        this.auth = auth;
    }

    /** What the logged-in user can open (drives the SevaSetu hub). */
    @GetMapping("/api/modules")
    public List<ModuleAccessService.ModuleDto> mine(HttpSession session) {
        return modules.modulesFor(auth.requireUser(session));
    }

    /** Public: which modules are live and which account types can sign up right now. */
    @GetMapping("/api/public/modules")
    public Map<String, Object> publicInfo() {
        return Map.of("modules", modules.publicModules(), "roles", modules.roleInfos());
    }
}
