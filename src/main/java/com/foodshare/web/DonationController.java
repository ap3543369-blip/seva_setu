package com.foodshare.web;

import com.foodshare.dto.Dtos.DonationDto;
import com.foodshare.dto.Dtos.DonationRequest;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.service.AuthService;
import com.foodshare.service.DonationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/donations")
public class DonationController {

    private final DonationService donations;
    private final AuthService auth;

    public DonationController(DonationService donations, AuthService auth) {
        this.donations = donations;
        this.auth = auth;
    }

    /** multipart/form-data: part "data" = JSON, optional parts "images" = up to 4 photos. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DonationDto create(@RequestPart("data") @Valid DonationRequest data,
                              @RequestPart(value = "images", required = false) List<MultipartFile> images,
                              HttpSession session) {
        User donor = auth.require(session, Role.DONOR);
        return donations.create(donor, data, images);
    }

    @DeleteMapping("/{id}")
    public DonationDto cancel(@PathVariable Long id, HttpSession session) {
        return donations.cancel(auth.require(session, Role.DONOR), id);
    }

    @GetMapping("/mine")
    public List<DonationDto> mine(HttpSession session) {
        return donations.mine(auth.require(session, Role.DONOR));
    }

    /** Public list for the Request Food page; "requested" is filled in when an NGO is logged in. */
    @GetMapping("/available")
    public List<DonationDto> available(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) Double maxDistance,
                                       HttpSession session) {
        User ngo = auth.currentUser(session).filter(u -> u.getRole() == Role.NGO).orElse(null);
        return donations.available(ngo, q, category, maxDistance);
    }
}
