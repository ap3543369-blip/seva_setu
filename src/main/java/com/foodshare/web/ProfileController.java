package com.foodshare.web;

import com.foodshare.dto.Dtos.PasswordRequest;
import com.foodshare.dto.Dtos.ProfileRequest;
import com.foodshare.dto.Dtos.UserDto;
import com.foodshare.service.AuthService;
import com.foodshare.service.Mapper;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final AuthService auth;

    public ProfileController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping
    public UserDto get(HttpSession session) {
        return Mapper.user(auth.requireUser(session));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(
            @Valid @RequestBody PasswordRequest body,
            HttpSession session) {

        auth.changePassword(
                auth.requireUser(session),
                body.currentPassword(),
                body.newPassword()
        );
    }

    @PutMapping
    public UserDto update(
            @Valid @RequestBody ProfileRequest body,
            HttpSession session) {

        return Mapper.user(
                auth.updateProfile(auth.requireUser(session), body)
        );
    }
}