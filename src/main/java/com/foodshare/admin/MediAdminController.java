package com.foodshare.admin;

import com.foodshare.medishare.MediService;
import com.foodshare.medishare.MediService.*;
import com.foodshare.model.Role;
import com.foodshare.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin side of MediShare: review queue, safety policy and audit trail. */
@RestController
@RequestMapping("/api/admin/medishare")
public class MediAdminController {
    private final MediService medi;
    private final AuthService auth;

    public MediAdminController(MediService medi, AuthService auth) {
        this.medi = medi;
        this.auth = auth;
    }

    @GetMapping("/donations")
    public List<MedicineDto> donations(HttpSession s) {
        return medi.reviewQueue(auth.require(s, Role.ADMIN));
    }

    @PostMapping("/donations/{id}/review")
    public MedicineDto review(@PathVariable Long id, @RequestBody @Valid ReviewBody body, HttpSession s) {
        return medi.review(auth.require(s, Role.ADMIN), id, body);
    }

    @GetMapping("/policy")
    public PolicyDto policy(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return medi.policyDto();
    }

    @PutMapping("/policy")
    public PolicyDto updatePolicy(@RequestBody @Valid PolicyBody body, HttpSession s) {
        return medi.updatePolicy(auth.require(s, Role.ADMIN), body);
    }

    @GetMapping("/audit")
    public List<AuditDto> audit(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return medi.auditTrail();
    }
}
