package com.foodshare.web;

import com.foodshare.dto.Dtos.AdminStats;
import com.foodshare.dto.Dtos.AdminUserDto;
import com.foodshare.dto.Dtos.DonationDto;
import com.foodshare.model.Role;
import com.foodshare.service.AdminService;
import com.foodshare.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final AuthService auth;

    public AdminController(AdminService admin, AuthService auth) {
        this.admin = admin;
        this.auth = auth;
    }

    @GetMapping("/stats")
    public AdminStats stats(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return admin.stats();
    }

    @GetMapping("/users")
    public List<AdminUserDto> users(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return admin.users();
    }

    @GetMapping("/donations")
    public List<DonationDto> donations(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return admin.donations();
    }

    @GetMapping("/messages")
    public List<com.foodshare.dto.Dtos.ContactMessageDto> messages(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return admin.messages();
    }

    @PostMapping("/messages/{id}/handled")
    public com.foodshare.dto.Dtos.ContactMessageDto handled(@PathVariable Long id, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return admin.toggleHandled(id);
    }

    @PostMapping("/users/{id}/block")
    public AdminUserDto block(@PathVariable Long id, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return admin.toggleBlock(id);
    }

    @PostMapping("/users/{id}/verify")
    public AdminUserDto verify(@PathVariable Long id, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return admin.verify(id);
    }
}
