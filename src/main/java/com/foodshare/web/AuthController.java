package com.foodshare.web;

import com.foodshare.dto.Dtos.LoginRequest;
import com.foodshare.dto.Dtos.SignupRequest;
import com.foodshare.dto.Dtos.UserDto;
import com.foodshare.model.User;
import com.foodshare.service.AuthService;
import com.foodshare.service.Mapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request) {
        return Mapper.user(auth.signup(body, request));
    }

    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return Mapper.user(auth.login(body, request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        auth.logout(session);
    }

    @GetMapping("/me")
    public UserDto me(HttpSession session) {
        User u = auth.requireUser(session);
        return Mapper.user(u);
    }
}
