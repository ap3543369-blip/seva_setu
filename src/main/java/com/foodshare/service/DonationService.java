package com.foodshare.service;

import com.foodshare.dto.Dtos.DonationDto;
import com.foodshare.dto.Dtos.DonationRequest;
import com.foodshare.model.Donation;
import com.foodshare.model.DonationStatus;
import com.foodshare.model.FoodRequest;
import com.foodshare.model.RequestStatus;
import com.foodshare.model.User;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.FoodRequestRepository;
import com.foodshare.repo.UserRepository;
import com.foodshare.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DonationService {

    static final int MAX_IMAGES = 4;

    private static final Set<String> UNITS = Set.of("kg", "portions", "packets", "litres");

    /** food type -> {filter category, placeholder picture} */
    private static final Map<String, String[]> FOOD_TYPES = Map.of(
            "Cooked Food", new String[]{"Cooked", "veg-biryani"},
            "Packed Food", new String[]{"Packed", "bread-packets"},
            "Bakery Items", new String[]{"Bakery", "bread-packets"},
            "Fruits & Vegetables", new String[]{"Vegetables", "mixed-vegetables"},
            "Raw Ingredients", new String[]{"Raw", "fruits"});

    private final DonationRepository donations;
    private final FoodRequestRepository requests;
    private final FileStorageService storage;
    private final UserRepository users;
    private final NotificationService notifications;
    private final com.foodshare.common.ModuleAccessService modules;

    public DonationService(DonationRepository donations, FoodRequestRepository requests,
                           FileStorageService storage, UserRepository users, NotificationService notifications,
                           com.foodshare.common.ModuleAccessService modules) {
        this.modules = modules;
        this.notifications = notifications;
        this.donations = donations;
        this.requests = requests;
        this.storage = storage;
        this.users = users;
    }

    @Transactional
    public DonationDto create(User donor, DonationRequest r, List<MultipartFile> images) {
        String[] type = FOOD_TYPES.get(r.foodType());
        if (type == null) {
            throw ApiException.badRequest("Please choose a valid food type.");
        }
        if (!UNITS.contains(r.unit())) {
            throw ApiException.badRequest("Please choose a valid unit.");
        }
        LocalDate today = LocalDate.now();
        if (r.bestDate().isBefore(today)) {
            throw ApiException.badRequest("Best-before date cannot be in the past.");
        }
        if (r.pickupDate().isBefore(today)) {
            throw ApiException.badRequest("Pickup date cannot be in the past.");
        }
        if (!r.timeTo().isAfter(r.timeFrom())) {
            throw ApiException.badRequest("End time must be after start time.");
        }
        List<MultipartFile> files = images == null ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (files.size() > MAX_IMAGES) {
            throw ApiException.badRequest("You can add up to " + MAX_IMAGES + " images.");
        }

        Donation d = new Donation();
        d.setDonor(donor);
        d.setFoodType(r.foodType());
        d.setCategory(type[0]);
        d.setImageKey(type[1]);
        d.setQuantity(r.quantity());
        d.setUnit(r.unit());
        d.setDescription(blankToNull(r.description()));
        d.setFoodCondition(r.condition());
        d.setBestDate(r.bestDate());
        d.setBestTime(r.bestTime());
        d.setAddress(r.address().trim());
        d.setCity(r.city().trim());
        d.setArea(r.city().trim());
        d.setPincode(r.pincode());
        d.setPhone(r.phone());
        d.setHandover(r.handover() == null || r.handover().isBlank() ? "NGO pickup" : r.handover());
        d.setPickupDate(r.pickupDate());
        d.setTimeFrom(r.timeFrom());
        d.setTimeTo(r.timeTo());
        d.setNotes(blankToNull(r.notes()));
        d.setMeals(mealsFor(r.quantity(), r.unit()));
        d.setPoints(pointsFor(r.quantity()));
        d.setDistanceKm(1 + Math.floorMod(r.pincode().hashCode(), 6)); // demo distance 1-6 km (no geocoding)
        d.setStatus(DonationStatus.PENDING);
        boolean needsApproval = modules.requiresApproval(com.foodshare.common.PlatformModule.FOODSHARE);
        d.setApproval(needsApproval ? com.foodshare.common.ApprovalStatus.PENDING : com.foodshare.common.ApprovalStatus.APPROVED);
        for (MultipartFile f : files) {
            d.getImageFiles().add(storage.storeImage(f));
        }
        donations.save(d);

        if (needsApproval) {
            // the admin sees it in Approvals → Food; points are given when it is approved
            for (User admin : users.findByRoleOrderByNameAsc(com.foodshare.model.Role.ADMIN)) {
                notifications.notify(admin, "New FOOD donation from " + donor.getName() + " (" + d.getFoodType() + ", "
                        + r.quantity() + " " + r.unit() + ") is waiting for your approval.");
            }
        } else {
            // points are earned when a donation is submitted ("donor" came from another transaction, so reload it)
            User managed = users.findById(donor.getId()).orElseThrow();
            managed.setPoints(managed.getPoints() + d.getPoints());
            users.save(managed);
        }
        return Mapper.donationFull(d);
    }

    /** A donor can cancel a donation while no NGO has accepted it. Points earned for it are taken back. */
    @Transactional
    public DonationDto cancel(User donor, Long id) {
        Donation d = donations.findById(id).orElseThrow(() -> ApiException.notFound("Donation not found."));
        if (!d.getDonor().getId().equals(donor.getId())) {
            throw ApiException.forbidden("This is not your donation.");
        }
        if (d.getStatus() != DonationStatus.PENDING) {
            throw ApiException.conflict("Only donations that are still pending can be cancelled.");
        }
        d.setStatus(DonationStatus.CANCELLED);
        for (FoodRequest r : requests.findByDonationAndStatus(d, RequestStatus.PENDING)) {
            r.setStatus(RequestStatus.DECLINED);
            notifications.notify(r.getNgo(), "The donor cancelled " + d.getFoodType() + ", so your request was closed.");
        }
        if (d.isApproved()) { // points were only given once the donation was approved
            User managed = users.findById(donor.getId()).orElseThrow();
            managed.setPoints(Math.max(0, managed.getPoints() - d.getPoints()));
            users.save(managed);
        }
        return Mapper.donationFull(d);
    }

    @Transactional(readOnly = true)
    public List<DonationDto> mine(User donor) {
        return donations.findByDonorOrderByCreatedAtDesc(donor).stream()
                .map(Mapper::donationFull).toList();
    }

    /** One donation. Address and phone are shown only to the donor, an admin, or the NGO that accepted it. */
    @Transactional(readOnly = true)
    public DonationDto detail(User viewerOrNull, Long id) {
        Donation d = donations.findById(id).orElseThrow(() -> ApiException.notFound("Donation not found."));
        boolean admin = viewerOrNull != null && viewerOrNull.getRole() == com.foodshare.model.Role.ADMIN;
        boolean owner = viewerOrNull != null && d.getDonor().getId().equals(viewerOrNull.getId());
        if ((d.getStatus() == DonationStatus.CANCELLED || !d.isApproved()) && !owner && !admin) {
            throw ApiException.notFound("Donation not found.");
        }
        boolean ngo = viewerOrNull != null && viewerOrNull.getRole() == com.foodshare.model.Role.NGO;
        boolean accepted = ngo && d.getNgo() != null && d.getNgo().getId().equals(viewerOrNull.getId());
        if (owner || admin || accepted) {
            return Mapper.donationFull(d);
        }
        return Mapper.donation(d, ngo && requests.existsByDonationAndNgo(d, viewerOrNull));
    }

    /** Donations still waiting for an NGO, filtered by text, category and distance. */
    @Transactional(readOnly = true)
    public List<DonationDto> available(User ngoOrNull, String q, String category, Double maxDistance) {
        Set<Long> already = ngoOrNull == null ? Set.of() : new HashSet<>(requests.findRequestedDonationIds(ngoOrNull));
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return donations.findByStatusOrderByCreatedAtDesc(DonationStatus.PENDING).stream()
                .filter(Donation::isApproved)
                .filter(d -> needle.isEmpty() || text(d).contains(needle))
                .filter(d -> category == null || category.isBlank() || d.getCategory().equalsIgnoreCase(category))
                .filter(d -> maxDistance == null || d.getDistanceKm() <= maxDistance)
                .map(d -> Mapper.donation(d, already.contains(d.getId()))).toList();
    }

    public static int mealsFor(double qty, String unit) {
        double meals = "kg".equals(unit) ? qty * 4 : "litres".equals(unit) ? qty * 3 : qty;
        return (int) Math.round(meals);
    }

    public static int pointsFor(double qty) {
        return (int) Math.min(500, Math.max(50, Math.round(qty * 10)));
    }

    private static String text(Donation d) {
        return String.join(" ", d.getFoodType(), d.getDonor().getName(), nullSafe(d.getArea()),
                d.getCategory(), nullSafe(d.getDescription())).toLowerCase(Locale.ROOT);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
