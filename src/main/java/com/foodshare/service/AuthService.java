package com.foodshare.service;

import com.foodshare.dto.Dtos.LoginRequest;
import com.foodshare.dto.Dtos.ProfileRequest;
import com.foodshare.dto.Dtos.SignupRequest;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    static final String SESSION_USER = "uid";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final com.foodshare.common.ModuleAccessService modules;

    public AuthService(UserRepository users, PasswordEncoder encoder, com.foodshare.common.ModuleAccessService modules) {
        this.users = users;
        this.encoder = encoder;
        this.modules = modules;
    }

    @Transactional
    public User signup(SignupRequest req, HttpServletRequest request) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        User u = new User();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        Role role = Role.valueOf(req.role().toUpperCase());
        if (role == Role.NGO) {
            throw ApiException.badRequest("NGOs register with the NGO registration form (it collects the organisation details for verification).");
        }
        if (!modules.roleAvailable(role)) {
            throw ApiException.badRequest("Sign-ups for this account type are not open yet.");
        }
        u.setRole(role);
        u.setOrgType(switch (role) {
            case NGO -> "NGO";
            case HOSPITAL -> "Hospital";
            case RECYCLER -> "Recycler";
            case RECIPIENT -> "Individual";
            default -> "Restaurant / Canteen";
        });
        u.setPoints(0);
        u.setVerified(!com.foodshare.common.ModuleAccessService.needsVerification(role)); // NGO / hospital / recycler wait for admin verification
        users.save(u);
        startSession(request, u);
        return u;
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest req, HttpServletRequest request) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Incorrect email or password."));
        if (u.isBlocked()) {
            throw ApiException.forbidden("This account has been blocked. Contact the SevaSetu admin.");
        }
        startSession(request, u);
        return u;
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    @Transactional
    public User updateProfile(User user, ProfileRequest req) {
        String email = req.email().trim().toLowerCase();
        Optional<User> other = users.findByEmailIgnoreCase(email);
        if (other.isPresent() && !other.get().getId().equals(user.getId())) {
            throw ApiException.conflict("That email is already used by another account.");
        }
        user.setName(req.name().trim());
        user.setOrgType(req.orgType().trim());
        user.setEmail(email);
        user.setPhone(req.phone().trim());
        return users.save(user);
    }

    @Transactional
    public void changePassword(User user, String current, String next) {
        User u = users.findById(user.getId()).orElseThrow();
        if (!encoder.matches(current, u.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is incorrect.");
        }
        u.setPasswordHash(encoder.encode(next));
        users.save(u);
    }

    @Transactional(readOnly = true)
    public Optional<User> currentUser(HttpSession session) {
        Object id = session.getAttribute(SESSION_USER);
        if (!(id instanceof Long)) {
            return Optional.empty();
        }
        return users.findById((Long) id);
    }

    /** The logged-in user, or 401 when nobody is logged in. */
    public User requireUser(HttpSession session) {
        return currentUser(session).orElseThrow(() -> ApiException.unauthorized("Please log in to continue."));
    }

    /** The logged-in user, 401 when logged out, 403 when the role does not match. */
    public User require(HttpSession session, Role role) {
        User u = requireUser(session);
        if (u.getRole() == Role.NGO && role == Role.NGO && !u.isVerified()) {
            throw ApiException.forbidden("Your NGO registration is waiting for admin approval.");
        }
        if (u.getRole() != role) {
            throw ApiException.forbidden(role == Role.NGO ? "Only NGO accounts can do this."
                    : role == Role.DONOR ? "Only donor accounts can do this."
                    : "Your account type cannot do this.");
        }
        return u;
    }

    /** Logs the user in (used after sign-up and after the NGO registration form). */
    public void startSession(HttpServletRequest request, User u) {
        HttpSession session = request.getSession(true);
        request.changeSessionId(); // prevents session fixation
        session.setAttribute(SESSION_USER, u.getId());
    }
}
