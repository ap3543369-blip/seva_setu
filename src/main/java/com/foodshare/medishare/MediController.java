package com.foodshare.medishare;

import com.foodshare.common.ModuleAccessService;
import com.foodshare.common.PlatformModule;
import com.foodshare.medishare.MediService.*;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.service.AuthService;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** MediShare REST API. Hospitals/pharmacies donate; verified NGOs and hospitals request. */
@RestController
@RequestMapping("/api/medishare")
public class MediController {
    private final MediService medi;
    private final AuthService auth;
    private final ModuleAccessService modules;

    public MediController(MediService medi, AuthService auth, ModuleAccessService modules) {
        this.medi = medi;
        this.auth = auth;
        this.modules = modules;
    }

    /** Logged in + module on + role allowed + verified (ModuleAccessService enforces verification for MediShare). */
    private User user(HttpSession s) {
        User u = auth.requireUser(s);
        modules.require(u, PlatformModule.MEDISHARE);
        return u;
    }

    private User withRole(HttpSession s, Role... roles) {
        User u = user(s);
        for (Role r : roles) if (u.getRole() == r) return u;
        throw ApiException.forbidden("Your account type cannot do this in MediShare.");
    }

    @GetMapping("/policy")
    public PolicyDto policy(HttpSession s) {
        user(s);
        return medi.policyDto();
    }

    @GetMapping("/stats")
    public Stats stats(HttpSession s) {
        return medi.stats(user(s));
    }

    @PostMapping("/donations")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicineDto create(@RequestBody @Valid MedicineForm body, HttpSession s) {
        return medi.create(withRole(s, Role.HOSPITAL), body);
    }

    @GetMapping("/donations/mine")
    public List<MedicineDto> mine(HttpSession s) {
        return medi.mine(withRole(s, Role.HOSPITAL));
    }

    @GetMapping("/donations/available")
    public List<MedicineDto> available(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
                                       HttpSession s) {
        return medi.available(withRole(s, Role.NGO, Role.HOSPITAL), q, category);
    }

    @GetMapping("/donations/{id}")
    public MedicineDto detail(@PathVariable Long id, HttpSession s) {
        return medi.detail(user(s), id);
    }

    @DeleteMapping("/donations/{id}")
    public MedicineDto withdraw(@PathVariable Long id, HttpSession s) {
        return medi.withdraw(withRole(s, Role.HOSPITAL), id);
    }

    @GetMapping("/donations/{id}/requests")
    public List<MediRequestDto> incoming(@PathVariable Long id, HttpSession s) {
        return medi.incoming(user(s), id);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public MediRequestDto request(@RequestBody @Valid CreateRequest body, HttpSession s) {
        return medi.createRequest(withRole(s, Role.NGO, Role.HOSPITAL), body);
    }

    @GetMapping("/requests/mine")
    public List<MediRequestDto> myRequests(HttpSession s) {
        return medi.myRequests(withRole(s, Role.NGO, Role.HOSPITAL));
    }

    @PatchMapping("/requests/{id}/status")
    public MediRequestDto status(@PathVariable Long id, @RequestBody @Valid StatusBody body, HttpSession s) {
        return medi.updateStatus(user(s), id, body);
    }
}
