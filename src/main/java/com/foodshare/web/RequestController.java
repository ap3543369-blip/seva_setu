package com.foodshare.web;

import com.foodshare.dto.Dtos.CreateFoodRequest;
import com.foodshare.dto.Dtos.FoodRequestDto;
import com.foodshare.dto.Dtos.StatusRequest;
import com.foodshare.model.Role;
import com.foodshare.service.AuthService;
import com.foodshare.service.RequestService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requests;
    private final AuthService auth;

    public RequestController(RequestService requests, AuthService auth) {
        this.requests = requests;
        this.auth = auth;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodRequestDto create(@Valid @RequestBody CreateFoodRequest body, HttpSession session) {
        return requests.create(auth.require(session, Role.NGO), body);
    }

    @GetMapping("/mine")
    public List<FoodRequestDto> mine(HttpSession session) {
        return requests.mine(auth.require(session, Role.NGO));
    }

    @PatchMapping("/{id}/status")
    public FoodRequestDto updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest body,
                                       HttpSession session) {
        return requests.updateStatus(auth.require(session, Role.NGO), id, body.status());
    }
}
