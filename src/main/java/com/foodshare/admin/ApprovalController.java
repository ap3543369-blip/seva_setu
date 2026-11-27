package com.foodshare.admin;

import com.foodshare.admin.ApprovalService.*;
import com.foodshare.model.Role;
import com.foodshare.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin approvals: what is waiting, by type, and the approve / reject decisions for food and book submissions. */
@RestController
@RequestMapping("/api/admin/approvals")
public class ApprovalController {
    private final ApprovalService approvals;
    private final AuthService auth;

    public ApprovalController(ApprovalService approvals, AuthService auth) {
        this.approvals = approvals;
        this.auth = auth;
    }

    @GetMapping("/summary")
    public Summary summary(HttpSession s) {
        auth.require(s, Role.ADMIN);
        return approvals.summary();
    }

    /** status = pending (default) or all */
    @GetMapping("/food")
    public List<FoodReviewDto> food(@RequestParam(defaultValue = "pending") String status, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return approvals.food(status);
    }

    @PostMapping("/food/{id}")
    public FoodReviewDto reviewFood(@PathVariable Long id, @RequestBody @Valid ReviewBody body, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return approvals.reviewFood(id, body);
    }

    @GetMapping("/books")
    public List<BookReviewDto> books(@RequestParam(defaultValue = "pending") String status, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return approvals.books(status);
    }

    @PostMapping("/books/{id}")
    public BookReviewDto reviewBook(@PathVariable Long id, @RequestBody @Valid ReviewBody body, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return approvals.reviewBook(id, body);
    }
}
