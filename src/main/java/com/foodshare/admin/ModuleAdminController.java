package com.foodshare.admin;

import com.foodshare.common.ModuleAccessService;
import com.foodshare.common.ModuleAccessService.AdminModuleDto;
import com.foodshare.common.ModuleAccessService.UserModuleDto;
import com.foodshare.common.PlatformModule;
import com.foodshare.model.Role;
import com.foodshare.service.AuthService;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin: switch modules on/off and grant/revoke a module for one user. */
@RestController
@RequestMapping("/api/admin")
public class ModuleAdminController {
    private final ModuleAccessService modules;
    private final AuthService auth;

    public ModuleAdminController(ModuleAccessService modules, AuthService auth) {
        this.modules = modules;
        this.auth = auth;
    }

    /** body for POST /users/{id}/modules/{key}: allowed true | false | null (null = back to the role default) */
    public record OverrideRequest(Boolean allowed) {}

    private static PlatformModule module(String key) {
        PlatformModule m = PlatformModule.fromKey(key);
        if (m == null) throw ApiException.notFound("Unknown module.");
        return m;
    }

    @GetMapping("/modules")
    public List<AdminModuleDto> list(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return modules.adminModules();
    }

    @PostMapping("/modules/{key}/toggle")
    public AdminModuleDto toggle(@PathVariable String key, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return modules.toggle(module(key));
    }

    @PostMapping("/modules/{key}/approval-toggle")
    public AdminModuleDto toggleApproval(@PathVariable String key, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return modules.toggleApproval(module(key));
    }

    @GetMapping("/users/{id}/modules")
    public List<UserModuleDto> userModules(@PathVariable Long id, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return modules.userModules(id);
    }

    @PostMapping("/users/{id}/modules/{key}")
    public List<UserModuleDto> override(@PathVariable Long id, @PathVariable String key,
                                        @RequestBody OverrideRequest body, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return modules.setOverride(id, module(key), body.allowed());
    }
}
