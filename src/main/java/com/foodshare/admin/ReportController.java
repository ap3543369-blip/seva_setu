package com.foodshare.admin;

import com.foodshare.bookshare.BookListingRepository;
import com.foodshare.bookshare.BookRequestRepository;
import com.foodshare.ecoai.EwastePickupRepository;
import com.foodshare.ecoai.EwasteRecord;
import com.foodshare.ecoai.EwasteRecordRepository;
import com.foodshare.medishare.MediAuditRepository;
import com.foodshare.medishare.MedicineDonationRepository;
import com.foodshare.medishare.MedicineRequestRepository;
import com.foodshare.model.Role;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** One cross-module report for the admin (counts only, no personal data). */
@RestController
@RequestMapping("/api/admin")
public class ReportController {
    private final AuthService auth;
    private final UserRepository users;
    private final DonationRepository donations;
    private final BookListingRepository bookListings;
    private final BookRequestRepository bookRequests;
    private final MedicineDonationRepository medicines;
    private final MedicineRequestRepository medicineRequests;
    private final MediAuditRepository audit;
    private final EwasteRecordRepository records;
    private final EwastePickupRepository pickups;

    public ReportController(AuthService auth, UserRepository users, DonationRepository donations,
                            BookListingRepository bookListings, BookRequestRepository bookRequests,
                            MedicineDonationRepository medicines, MedicineRequestRepository medicineRequests,
                            MediAuditRepository audit, EwasteRecordRepository records, EwastePickupRepository pickups) {
        this.auth = auth;
        this.users = users;
        this.donations = donations;
        this.bookListings = bookListings;
        this.bookRequests = bookRequests;
        this.medicines = medicines;
        this.medicineRequests = medicineRequests;
        this.audit = audit;
        this.records = records;
        this.pickups = pickups;
    }

    private static <T> Map<String, Long> count(List<T> list, Function<T, Object> key) {
        return list.stream().collect(Collectors.groupingBy(t -> String.valueOf(key.apply(t)), TreeMap::new, Collectors.counting()));
    }

    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public Map<String, Object> report(HttpSession session) {
        auth.require(session, Role.ADMIN);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", LocalDateTime.now());

        Map<String, Object> u = new LinkedHashMap<>();
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Role r : Role.values()) byRole.put(r.key(), users.countByRole(r));
        u.put("byRole", byRole);
        u.put("pendingVerification", users.countByRoleAndVerifiedFalse(Role.NGO) + users.countByRoleAndVerifiedFalse(Role.HOSPITAL)
                + users.countByRoleAndVerifiedFalse(Role.RECYCLER));
        out.put("users", u);

        out.put("foodshare", Map.of("donations", donations.count(), "byStatus", count(donations.findAll(), d -> d.getStatus())));

        var listings = bookListings.findAll();
        var breq = bookRequests.findAll();
        out.put("bookshare", Map.of("listings", listings.size(), "listingsByStatus", count(listings, b -> b.getStatus()),
                "requests", breq.size(), "requestsByStatus", count(breq, r -> r.getStatus()),
                "copiesHandedOver", breq.stream().filter(r -> r.getStatus().name().equals("COMPLETED")).mapToLong(r -> r.getQuantity()).sum()));

        var meds = medicines.findAll();
        var mreq = medicineRequests.findAll();
        out.put("medishare", Map.of("donations", meds.size(), "donationsByStatus", count(meds, d -> d.getStatus()),
                "requests", mreq.size(), "requestsByStatus", count(mreq, r -> r.getStatus()), "auditEntries", audit.count()));

        var recs = records.findAll();
        var pk = pickups.findAll();
        double kg = recs.stream().mapToDouble(EwasteRecord::getKg).sum();
        out.put("ecoai", Map.of("kgRecorded", Math.round(kg * 10.0) / 10.0, "monthsOfData", recs.stream().map(EwasteRecord::getMonthKey).distinct().count(),
                "pickups", pk.size(), "pickupsByStatus", count(pk, p -> p.getStatus()), "includesDemoData", recs.stream().anyMatch(EwasteRecord::isSeeded)));
        return out;
    }
}
