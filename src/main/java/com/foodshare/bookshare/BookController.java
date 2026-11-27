package com.foodshare.bookshare;

import com.foodshare.bookshare.BookService.*;
import com.foodshare.common.ModuleAccessService;
import com.foodshare.common.PlatformModule;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.service.AuthService;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** BookShare REST API. Donors list books; recipients and NGOs request them. */
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService books;
    private final AuthService auth;
    private final ModuleAccessService modules;

    public BookController(BookService books, AuthService auth, ModuleAccessService modules) {
        this.books = books;
        this.auth = auth;
        this.modules = modules;
    }

    /** Logged in + BookShare switched on + allowed for this user. */
    private User user(HttpSession s) {
        User u = auth.requireUser(s);
        modules.require(u, PlatformModule.BOOKSHARE);
        return u;
    }

    private User withRole(HttpSession s, Role... roles) {
        User u = user(s);
        for (Role r : roles) if (u.getRole() == r) return u;
        throw ApiException.forbidden("Your account type cannot do this in BookShare.");
    }

    /** multipart: part "data" = JSON, optional parts "images" (max 4). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BookDto create(@RequestPart("data") @Valid BookForm data,
                          @RequestPart(value = "images", required = false) List<MultipartFile> images,
                          HttpSession s) {
        return books.create(withRole(s, Role.DONOR), data, images);
    }

    @GetMapping("/mine")
    public List<BookDto> mine(HttpSession s) {
        return books.mine(withRole(s, Role.DONOR));
    }

    @GetMapping("/available")
    public List<BookDto> available(@RequestParam(required = false) String q, @RequestParam(required = false) String subject,
                                   @RequestParam(required = false) String level, @RequestParam(required = false) String city,
                                   HttpSession s) {
        return books.available(user(s), q, subject, level, city);
    }

    @GetMapping("/stats")
    public Stats stats(HttpSession s) {
        return books.stats(user(s));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public BookRequestDto request(@RequestBody @Valid CreateRequest body, HttpSession s) {
        return books.createRequest(withRole(s, Role.RECIPIENT, Role.NGO), body);
    }

    @GetMapping("/requests/mine")
    public List<BookRequestDto> myRequests(HttpSession s) {
        return books.myRequests(withRole(s, Role.RECIPIENT, Role.NGO));
    }

    @PatchMapping("/requests/{id}/status")
    public BookRequestDto status(@PathVariable Long id, @RequestBody @Valid StatusBody body, HttpSession s) {
        return books.updateStatus(user(s), id, body.status());
    }

    @GetMapping("/{id}")
    public BookDto detail(@PathVariable Long id, HttpSession s) {
        return books.detail(user(s), id);
    }

    @DeleteMapping("/{id}")
    public BookDto cancel(@PathVariable Long id, HttpSession s) {
        return books.cancel(withRole(s, Role.DONOR), id);
    }

    @GetMapping("/{id}/requests")
    public List<BookRequestDto> forListing(@PathVariable Long id, HttpSession s) {
        return books.forListing(user(s), id);
    }
}
