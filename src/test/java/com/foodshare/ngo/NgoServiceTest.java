package com.foodshare.ngo;

import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.ngo.NgoService.NgoForm;
import com.foodshare.ngo.NgoService.ReviewBody;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.AuthService;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NgoServiceTest {
    private NgoProfileRepository profiles;
    private UserRepository users;
    private NgoDocumentStorage storage;
    private AuthService auth;
    private NgoService service;

    @BeforeEach
    void setUp() {
        profiles = mock(NgoProfileRepository.class);
        users = mock(UserRepository.class);
        storage = mock(NgoDocumentStorage.class);
        auth = mock(AuthService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(profiles.save(any(NgoProfile.class))).thenAnswer(i -> i.getArgument(0));
        when(storage.store(any())).thenReturn("0123456789abcdef0123456789abcdef.pdf");
        service = new NgoService(profiles, users, encoder, storage, mock(NotificationService.class), auth);
    }

    private NgoForm form(String email, String password, boolean declaration) {
        return new NgoForm(email, password, "Hope Foundation", "Trust", "REG/2019/45", 2019, "Meera", "9876543210", "21 Lane",
                "Delhi", "Delhi", "110024", "", "Meals and school supplies", null, 1200, declaration);
    }

    @Test
    void registrationCreatesAnUnverifiedNgoWithASubmittedApplicationAndLogsItIn() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        var dto = service.register(form("Hope@Example.org", "secret1", true), mock(MultipartFile.class), request);
        assertEquals("SUBMITTED", dto.status());
        assertTrue(dto.hasDocument());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(Role.NGO, saved.getValue().getRole());
        assertFalse(saved.getValue().isVerified());
        assertEquals("hope@example.org", saved.getValue().getEmail());
        verify(auth).startSession(eq(request), any(User.class));
    }

    @Test
    void registrationRejectsBadInputBeforeSavingAnything() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertThrows(ApiException.class, () -> service.register(form("not-an-email", "secret1", true), null, request));
        assertThrows(ApiException.class, () -> service.register(form("a@b.org", "123", true), null, request));
        assertThrows(ApiException.class, () -> service.register(form("a@b.org", "secret1", false), null, request)); // declaration not ticked
        when(users.existsByEmailIgnoreCase("taken@b.org")).thenReturn(true);
        assertEquals(409, assertThrows(ApiException.class, () -> service.register(form("taken@b.org", "secret1", true), null, request)).getStatus().value());
        when(profiles.existsByRegistrationNumberIgnoreCase("REG/2019/45")).thenReturn(true);
        assertEquals(409, assertThrows(ApiException.class, () -> service.register(form("new@b.org", "secret1", true), null, request)).getStatus().value());
        verify(users, never()).save(any(User.class));
        verify(profiles, never()).save(any(NgoProfile.class));
    }

    private NgoProfile submitted() {
        User u = new User();
        u.setId(5L);
        u.setName("Hope Foundation");
        u.setRole(Role.NGO);
        u.setVerified(false);
        NgoProfile p = new NgoProfile();
        p.setId(1L);
        p.setUser(u);
        p.setRegistrationNumber("REG/1");
        p.setStatus(NgoApplicationStatus.SUBMITTED);
        when(profiles.findById(1L)).thenReturn(Optional.of(p));
        when(users.findById(5L)).thenReturn(Optional.of(u));
        return p;
    }

    @Test
    void approvingVerifiesTheNgoAndRejectingNeedsAReason() {
        NgoProfile p = submitted();
        User admin = new User();
        admin.setRole(Role.ADMIN);
        assertThrows(ApiException.class, () -> service.review(admin, 1L, new ReviewBody("reject", " ")));
        assertFalse(p.getUser().isVerified());
        service.review(admin, 1L, new ReviewBody("approve", null));
        assertTrue(p.getUser().isVerified());
        assertEquals(NgoApplicationStatus.APPROVED, p.getStatus());
        // reviewed once: a second decision is refused
        assertEquals(409, assertThrows(ApiException.class, () -> service.review(admin, 1L, new ReviewBody("reject", "late"))).getStatus().value());
    }

    @Test
    void rejectingKeepsTheNgoUnverifiedAndStoresTheReason() {
        NgoProfile p = submitted();
        User admin = new User();
        admin.setRole(Role.ADMIN);
        service.review(admin, 1L, new ReviewBody("reject", "Certificate is unreadable"));
        assertFalse(p.getUser().isVerified());
        assertEquals(NgoApplicationStatus.REJECTED, p.getStatus());
        assertEquals("Certificate is unreadable", p.getReviewNote());
        assertThrows(ApiException.class, () -> service.review(admin, 1L, new ReviewBody("maybe", null)));
    }
}
