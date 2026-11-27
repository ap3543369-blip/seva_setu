package com.foodshare.web;

import com.foodshare.dto.Dtos.ContactRequest;
import com.foodshare.dto.Dtos.DonationDto;
import com.foodshare.dto.Dtos.NgoDto;
import com.foodshare.dto.Dtos.StoryDto;
import com.foodshare.model.ContactMessage;
import com.foodshare.repo.ContactMessageRepository;
import com.foodshare.repo.StoryRepository;
import com.foodshare.service.AuthService;
import com.foodshare.service.DashboardService;
import com.foodshare.service.DonationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Endpoints that need no login: NGO directory, stories, contact form, donation details. */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final DashboardService dashboards;
    private final StoryRepository stories;
    private final ContactMessageRepository contacts;
    private final DonationService donations;
    private final AuthService auth;

    public PublicController(DashboardService dashboards, StoryRepository stories, ContactMessageRepository contacts,
                            DonationService donations, AuthService auth) {
        this.dashboards = dashboards;
        this.stories = stories;
        this.contacts = contacts;
        this.donations = donations;
        this.auth = auth;
    }

    @GetMapping("/ngos")
    public List<NgoDto> ngos() {
        return dashboards.ngoDirectory();
    }

    @GetMapping("/stories")
    public List<StoryDto> stories() {
        return stories.findAll().stream()
                .map(s -> new StoryDto(s.getId(), s.getName(), s.getRole(), s.getQuote(), s.getAvatar())).toList();
    }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> contact(@Valid @RequestBody ContactRequest body) {
        ContactMessage m = new ContactMessage();
        m.setName(body.name().trim());
        m.setEmail(body.email().trim());
        m.setSubject(body.subject().trim());
        m.setMessage(body.message().trim());
        contacts.save(m);
        return Map.of("message", "Thanks! We will get back to you soon.");
    }

    @GetMapping("/donations/{id}")
    public DonationDto donation(@PathVariable Long id, HttpSession session) {
        return donations.detail(auth.currentUser(session).orElse(null), id);
    }
}
