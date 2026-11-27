package com.foodshare.medishare;

import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * MediShare business rules. Safety first: only verified hospitals/pharmacies list items, every item is checked
 * against the admin-editable {@link MediPolicy}, an admin reviews listings (unless switched off), requesters must be
 * verified NGOs/hospitals with a registration number, and every action is written to the audit trail.
 */
@Service
public class MediService {

    // ---------------------------------------------------------------- shapes
    public record MedicineForm(
            @NotBlank(message = "Choose a category.") String category,
            @NotBlank(message = "Enter the medicine / item name.") @Size(max = 150) String medicineName,
            @Size(max = 60) String strength,
            @Size(max = 60) String dosageForm,
            @NotBlank(message = "Batch number is required.") @Size(max = 60) String batchNumber,
            @NotNull(message = "Expiry date is required.") LocalDate expiryDate,
            @Min(value = 1, message = "At least 1 unit.") @Max(value = 100000, message = "Quantity is too large.") int quantity,
            @NotBlank(message = "Enter a unit (e.g. strips, bottles, boxes).") @Size(max = 30) String unit,
            boolean sealedOriginalPack,
            @NotBlank(message = "Choose the storage condition.") String storage,
            @NotBlank(message = "Your licence / registration number is required.") @Size(min = 3, max = 60) String licenceNumber,
            @Size(max = 1000) String notes,
            @NotBlank(message = "Pickup address is required.") String address,
            @NotBlank(message = "City is required.") String city,
            @Pattern(regexp = "[6-9]\\d{9}", message = "Enter a valid 10-digit mobile number.") String phone,
            boolean declareAuthorised, boolean declareNotControlled, boolean declareUnexpiredAndSafe) {
    }

    public record CreateRequest(@NotNull(message = "Choose an item.") Long donationId,
                                @Min(value = 1, message = "At least 1 unit.") int quantity,
                                @NotBlank(message = "Say how the items will be used.") @Size(max = 500) String purpose,
                                @NotBlank(message = "Your registration / licence number is required.") @Size(min = 3, max = 60) String registrationNumber) {
    }

    public record StatusBody(@NotBlank String status, @Size(max = 500) String note) {
    }

    public record ReviewBody(@NotBlank String decision, @Size(max = 500) String note) {
    }

    public record PolicyBody(@Min(0) @Max(1825) int minShelfLifeDays, boolean requireAdminReview, boolean requireSealedPack,
                             boolean allowColdChain, @NotBlank String allowedCategories, String blockedTerms) {
    }

    public record PolicyDto(int minShelfLifeDays, boolean requireAdminReview, boolean requireSealedPack,
                            boolean allowColdChain, List<String> allowedCategories, List<String> blockedTerms,
                            List<String> storageOptions, String notice) {
    }

    public record MedicineDto(Long id, String category, String medicineName, String strength, String dosageForm,
                              String batchNumber, LocalDate expiryDate, long daysToExpiry, int quantity, int quantityLeft,
                              String unit, String storage, boolean sealedOriginalPack, String licenceNumber, String notes,
                              String donor, String city, String address, String phone, String status, String statusLabel,
                              String reviewNote, LocalDateTime createdAt, Long pendingRequests, String myRequestStatus) {
    }

    public record MediRequestDto(Long id, Long donationId, String medicine, int quantity, String unit, String purpose,
                                 String registrationNumber, String status, String decisionNote, String counterpart,
                                 String phone, String address, LocalDateTime createdAt) {
    }

    public record AuditDto(Long id, String actor, String role, String action, String targetType, Long targetId,
                           String detail, LocalDateTime createdAt) {
    }

    public record Stats(long listed, long pendingReview, long unitsAllocated, long requestsMade, long requestsApproved,
                        long requestsToReview) {
    }

    public static final List<String> STORAGE = List.of("Room temperature", "Cool & dry place", "Refrigerated (cold chain)");
    static final String NOTICE = "MediShare is for authorised healthcare organisations only. Controlled, narcotic, psychotropic and "
            + "cold-chain items are blocked by default. These rules are set by the platform admin and must be confirmed by a "
            + "qualified pharmacist / legal adviser for your jurisdiction. Nothing here is medical or legal advice.";

    private final MedicineDonationRepository donations;
    private final MedicineRequestRepository requests;
    private final MediPolicyRepository policies;
    private final MediAuditRepository audit;
    private final UserRepository users;
    private final NotificationService notifications;

    public MediService(MedicineDonationRepository donations, MedicineRequestRepository requests, MediPolicyRepository policies,
                       MediAuditRepository audit, UserRepository users, NotificationService notifications) {
        this.donations = donations;
        this.requests = requests;
        this.policies = policies;
        this.audit = audit;
        this.users = users;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- policy
    @Transactional
    public MediPolicy policy() {
        return policies.findById(1L).orElseGet(() -> policies.save(new MediPolicy()));
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Transactional
    public PolicyDto policyDto() {
        MediPolicy p = policy();
        return new PolicyDto(p.getMinShelfLifeDays(), p.isRequireAdminReview(), p.isRequireSealedPack(), p.isAllowColdChain(),
                split(p.getAllowedCategories()), split(p.getBlockedTerms()), STORAGE, NOTICE);
    }

    @Transactional
    public PolicyDto updatePolicy(User admin, PolicyBody b) {
        MediPolicy p = policy();
        if (split(b.allowedCategories()).isEmpty()) throw ApiException.badRequest("Keep at least one allowed category.");
        p.setMinShelfLifeDays(b.minShelfLifeDays());
        p.setRequireAdminReview(b.requireAdminReview());
        p.setRequireSealedPack(b.requireSealedPack());
        p.setAllowColdChain(b.allowColdChain());
        p.setAllowedCategories(String.join(",", split(b.allowedCategories())));
        p.setBlockedTerms(String.join(",", split(b.blockedTerms())));
        policies.save(p);
        log(admin, "POLICY_UPDATED", "policy", 1L, "shelfLife=" + p.getMinShelfLifeDays() + "d, review=" + p.isRequireAdminReview()
                + ", sealed=" + p.isRequireSealedPack() + ", coldChain=" + p.isAllowColdChain());
        return policyDto();
    }

    // ---------------------------------------------------------------- donor (hospital / pharmacy)
    @Transactional
    public MedicineDto create(User hospital, MedicineForm f) {
        MediPolicy p = policy();
        if (!f.declareAuthorised()) throw ApiException.badRequest("Confirm that you are authorised to donate these items.");
        if (!f.declareNotControlled()) throw ApiException.badRequest("Confirm that the items are not controlled or prescription-restricted substances.");
        if (!f.declareUnexpiredAndSafe()) throw ApiException.badRequest("Confirm that the items are unexpired, undamaged and stored correctly.");
        if (!split(p.getAllowedCategories()).contains(f.category())) throw ApiException.badRequest("This category is not accepted.");
        if (!STORAGE.contains(f.storage())) throw ApiException.badRequest("Choose a valid storage condition.");
        if (f.storage().startsWith("Refrigerated") && !p.isAllowColdChain()) {
            throw ApiException.badRequest("Cold-chain items are not accepted on this platform.");
        }
        if (p.isRequireSealedPack() && !f.sealedOriginalPack()) {
            throw ApiException.badRequest("Only sealed, original-pack items can be listed.");
        }
        String haystack = (f.medicineName() + " " + nz(f.strength()) + " " + nz(f.dosageForm()) + " " + nz(f.notes())).toLowerCase(Locale.ROOT);
        for (String term : split(p.getBlockedTerms())) {
            if (haystack.contains(term.toLowerCase(Locale.ROOT))) {
                log(hospital, "DONATION_BLOCKED", "donation", null, "blocked term: " + term + " (" + f.medicineName() + ")");
                throw ApiException.badRequest("This item matches a restricted term (\"" + term + "\") and cannot be listed.");
            }
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), f.expiryDate());
        if (days < p.getMinShelfLifeDays()) {
            throw ApiException.badRequest("Items must have at least " + p.getMinShelfLifeDays() + " days of shelf life left (this one has " + Math.max(days, 0) + ").");
        }

        MedicineDonation d = new MedicineDonation();
        d.setDonor(hospital);
        d.setCategory(f.category());
        d.setMedicineName(f.medicineName().trim());
        d.setStrength(blankToNull(f.strength()));
        d.setDosageForm(blankToNull(f.dosageForm()));
        d.setBatchNumber(f.batchNumber().trim());
        d.setExpiryDate(f.expiryDate());
        d.setQuantity(f.quantity());
        d.setQuantityLeft(f.quantity());
        d.setUnit(f.unit().trim());
        d.setSealedOriginalPack(f.sealedOriginalPack());
        d.setStorage(f.storage());
        d.setLicenceNumber(f.licenceNumber().trim());
        d.setNotes(blankToNull(f.notes()));
        d.setAddress(f.address().trim());
        d.setCity(f.city().trim());
        d.setPhone(f.phone());
        d.setStatus(p.isRequireAdminReview() ? MediStatus.PENDING_REVIEW : MediStatus.LISTED);
        donations.save(d);
        log(hospital, "DONATION_CREATED", "donation", d.getId(), d.getMedicineName() + " x" + d.getQuantity() + " " + d.getUnit() + ", batch " + d.getBatchNumber() + ", status " + d.getStatus());
        if (d.getStatus() == MediStatus.PENDING_REVIEW) {
            for (User a : users.findByRoleOrderByNameAsc(Role.ADMIN)) {
                notifications.notify(a, hospital.getName() + " submitted \"" + d.getMedicineName() + "\" for MediShare review.");
            }
        }
        return dto(d, hospital);
    }

    @Transactional(readOnly = true)
    public List<MedicineDto> mine(User donor) {
        return donations.findByDonorOrderByCreatedAtDesc(donor).stream().map(d -> dto(d, donor)).toList();
    }

    @Transactional
    public MedicineDto withdraw(User donor, Long id) {
        MedicineDonation d = find(id);
        if (!d.getDonor().getId().equals(donor.getId())) throw ApiException.forbidden("This is not your listing.");
        if (d.getStatus() == MediStatus.WITHDRAWN || d.getStatus() == MediStatus.REJECTED) throw ApiException.conflict("Already closed.");
        if (requests.countByDonationAndStatus(d, MediRequestStatus.APPROVED) > 0) {
            throw ApiException.conflict("Resolve the approved requests (mark collected or reject) before withdrawing.");
        }
        rejectPending(d, "The listing was withdrawn by the donor.");
        d.setStatus(MediStatus.WITHDRAWN);
        donations.save(d);
        log(donor, "DONATION_WITHDRAWN", "donation", d.getId(), d.getMedicineName());
        return dto(d, donor);
    }

    @Transactional(readOnly = true)
    public List<MediRequestDto> incoming(User donor, Long donationId) {
        MedicineDonation d = find(donationId);
        if (donor.getRole() != Role.ADMIN && !d.getDonor().getId().equals(donor.getId())) throw ApiException.forbidden("This is not your listing.");
        return requests.findByDonationOrderByCreatedAtDesc(d).stream().map(r -> requestDto(r, donor)).toList();
    }

    // ---------------------------------------------------------------- browsing (verified NGO / hospital)
    @Transactional(readOnly = true)
    public List<MedicineDto> available(User viewer, String q, String category) {
        MediPolicy p = policies.findById(1L).orElseGet(MediPolicy::new);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return donations.findByStatusOrderByCreatedAtDesc(MediStatus.LISTED).stream()
                .filter(d -> d.getQuantityLeft() > 0 && !d.getDonor().getId().equals(viewer.getId()))
                .filter(d -> ChronoUnit.DAYS.between(LocalDate.now(), d.getExpiryDate()) >= p.getMinShelfLifeDays())
                .filter(d -> needle.isEmpty() || (d.getMedicineName() + " " + nz(d.getStrength())).toLowerCase(Locale.ROOT).contains(needle))
                .filter(d -> category == null || category.isBlank() || d.getCategory().equals(category))
                .map(d -> dto(d, viewer)).toList();
    }

    @Transactional(readOnly = true)
    public MedicineDto detail(User viewer, Long id) {
        MedicineDonation d = find(id);
        boolean owner = d.getDonor().getId().equals(viewer.getId());
        if (!owner && viewer.getRole() != Role.ADMIN && d.getStatus() != MediStatus.LISTED
                && requests.findByDonationAndRequester(d, viewer).isEmpty()) {
            throw ApiException.notFound("Item not found.");
        }
        return dto(d, viewer);
    }

    // ---------------------------------------------------------------- requester
    @Transactional
    public MediRequestDto createRequest(User requester, CreateRequest body) {
        MediPolicy p = policy();
        MedicineDonation d = find(body.donationId());
        if (d.getDonor().getId().equals(requester.getId())) throw ApiException.badRequest("You cannot request your own listing.");
        if (d.getStatus() != MediStatus.LISTED) throw ApiException.conflict("This item is not available.");
        requireShelfLife(d, p);
        if (body.quantity() > d.getQuantityLeft()) throw ApiException.badRequest("Only " + d.getQuantityLeft() + " " + d.getUnit() + " left.");
        if (requests.findByDonationAndRequester(d, requester).isPresent()) throw ApiException.conflict("You have already requested this item.");
        MedicineRequest r = new MedicineRequest();
        r.setDonation(d);
        r.setRequester(requester);
        r.setQuantity(body.quantity());
        r.setPurpose(body.purpose().trim());
        r.setRegistrationNumber(body.registrationNumber().trim());
        requests.save(r);
        log(requester, "REQUEST_CREATED", "request", r.getId(), d.getMedicineName() + " x" + r.getQuantity() + ", reg " + r.getRegistrationNumber());
        notifications.notify(d.getDonor(), requester.getName() + " requested " + r.getQuantity() + " " + d.getUnit() + " of \"" + d.getMedicineName() + "\".");
        return requestDto(r, requester);
    }

    @Transactional(readOnly = true)
    public List<MediRequestDto> myRequests(User requester) {
        return requests.findByRequesterOrderByCreatedAtDesc(requester).stream().map(r -> requestDto(r, requester)).toList();
    }

    @Transactional
    public MediRequestDto updateStatus(User actor, Long requestId, StatusBody body) {
        MedicineRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request not found."));
        MedicineDonation d = r.getDonation();
        MediRequestStatus next;
        try {
            next = MediRequestStatus.valueOf(body.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status.");
        }
        MediRequestStatus cur = r.getStatus();
        boolean isDonor = d.getDonor().getId().equals(actor.getId());
        boolean isRequester = r.getRequester().getId().equals(actor.getId());
        String note = blankToNull(body.note());

        if (isDonor) {
            if (cur == MediRequestStatus.PENDING && next == MediRequestStatus.APPROVED) {
                if (d.getStatus() != MediStatus.LISTED) throw ApiException.conflict("This listing is not active.");
                requireShelfLife(d, policy());
                if (r.getQuantity() > d.getQuantityLeft()) throw ApiException.conflict("Not enough units left.");
                d.setQuantityLeft(d.getQuantityLeft() - r.getQuantity());
                if (d.getQuantityLeft() == 0) d.setStatus(MediStatus.FULLY_ALLOCATED);
                notifications.notify(r.getRequester(), "Your request for \"" + d.getMedicineName() + "\" was approved. Pickup details are now visible.");
            } else if (cur == MediRequestStatus.PENDING && next == MediRequestStatus.REJECTED) {
                notifications.notify(r.getRequester(), "Your request for \"" + d.getMedicineName() + "\" was not approved.");
            } else if (cur == MediRequestStatus.APPROVED && next == MediRequestStatus.REJECTED) {
                restore(d, r);
                notifications.notify(r.getRequester(), "The donor cancelled the approved handover of \"" + d.getMedicineName() + "\".");
            } else if (cur == MediRequestStatus.APPROVED && next == MediRequestStatus.COLLECTED) {
                requireShelfLife(d, policy());
                notifications.notify(r.getRequester(), "Handover of \"" + d.getMedicineName() + "\" is recorded as collected.");
            } else {
                throw ApiException.badRequest("This status change is not allowed.");
            }
        } else if (isRequester) {
            if ((cur == MediRequestStatus.PENDING || cur == MediRequestStatus.APPROVED) && next == MediRequestStatus.CANCELLED) {
                if (cur == MediRequestStatus.APPROVED) restore(d, r);
                notifications.notify(d.getDonor(), r.getRequester().getName() + " cancelled the request for \"" + d.getMedicineName() + "\".");
            } else {
                throw ApiException.badRequest("You can only cancel a pending or approved request.");
            }
        } else {
            throw ApiException.forbidden("This request is not yours.");
        }
        r.setStatus(next);
        if (note != null) r.setDecisionNote(note);
        requests.save(r);
        donations.save(d);
        log(actor, "REQUEST_" + next.name(), "request", r.getId(), d.getMedicineName() + " x" + r.getQuantity() + (note == null ? "" : " — " + note));
        return requestDto(r, actor);
    }

    // ---------------------------------------------------------------- admin
    @Transactional(readOnly = true)
    public List<MedicineDto> reviewQueue(User admin) {
        return donations.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> d.getStatus() == MediStatus.PENDING_REVIEW || d.getStatus() == MediStatus.LISTED || d.getStatus() == MediStatus.SUSPENDED)
                .map(d -> dto(d, admin)).toList();
    }

    @Transactional
    public MedicineDto review(User admin, Long id, ReviewBody b) {
        MedicineDonation d = find(id);
        String decision = b.decision().trim().toLowerCase(Locale.ROOT);
        String note = blankToNull(b.note());
        switch (decision) {
            case "approve" -> {
                if (d.getStatus() != MediStatus.PENDING_REVIEW && d.getStatus() != MediStatus.SUSPENDED) throw ApiException.conflict("Nothing to approve.");
                requireShelfLife(d, policy());
                d.setStatus(MediStatus.LISTED);
                notifications.notify(d.getDonor(), "\"" + d.getMedicineName() + "\" was approved and is now listed.");
            }
            case "reject" -> {
                if (d.getStatus() != MediStatus.PENDING_REVIEW) throw ApiException.conflict("Only items waiting for review can be rejected.");
                if (note == null) throw ApiException.badRequest("Please give a reason for rejecting.");
                d.setStatus(MediStatus.REJECTED);
                notifications.notify(d.getDonor(), "\"" + d.getMedicineName() + "\" was rejected: " + note);
            }
            case "suspend" -> {
                if (d.getStatus() != MediStatus.LISTED) throw ApiException.conflict("Only listed items can be suspended.");
                if (note == null) throw ApiException.badRequest("Please give a reason for suspending.");
                rejectPending(d, "The listing was suspended by an admin.");
                d.setStatus(MediStatus.SUSPENDED);
                notifications.notify(d.getDonor(), "\"" + d.getMedicineName() + "\" was suspended: " + note);
            }
            default -> throw ApiException.badRequest("Decision must be approve, reject or suspend.");
        }
        d.setReviewNote(note);
        donations.save(d);
        log(admin, "REVIEW_" + decision.toUpperCase(Locale.ROOT), "donation", d.getId(), d.getMedicineName() + (note == null ? "" : " — " + note));
        return dto(d, admin);
    }

    @Transactional(readOnly = true)
    public List<AuditDto> auditTrail() {
        return audit.findTop200ByOrderByCreatedAtDesc().stream().map(a -> new AuditDto(a.getId(), a.getActorName(), a.getActorRole(),
                a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetail(), a.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public Stats stats(User u) {
        List<MedicineDonation> own = donations.findByDonorOrderByCreatedAtDesc(u);
        long listed = own.stream().filter(d -> d.getStatus() == MediStatus.LISTED).count();
        long review = own.stream().filter(d -> d.getStatus() == MediStatus.PENDING_REVIEW).count();
        long allocated = 0, toReview = 0;
        for (MedicineDonation d : own) {
            for (MedicineRequest r : requests.findByDonationOrderByCreatedAtDesc(d)) {
                if (r.getStatus() == MediRequestStatus.APPROVED || r.getStatus() == MediRequestStatus.COLLECTED) allocated += r.getQuantity();
                if (r.getStatus() == MediRequestStatus.PENDING) toReview++;
            }
        }
        List<MedicineRequest> mine = requests.findByRequesterOrderByCreatedAtDesc(u);
        long approved = mine.stream().filter(r -> r.getStatus() == MediRequestStatus.APPROVED || r.getStatus() == MediRequestStatus.COLLECTED).count();
        return new Stats(listed, review, allocated, mine.size(), approved, toReview);
    }

    // ---------------------------------------------------------------- helpers
    private void requireShelfLife(MedicineDonation d, MediPolicy p) {
        if (ChronoUnit.DAYS.between(LocalDate.now(), d.getExpiryDate()) < p.getMinShelfLifeDays()) {
            throw ApiException.conflict("This item no longer meets the minimum shelf-life rule.");
        }
    }

    private void restore(MedicineDonation d, MedicineRequest r) {
        d.setQuantityLeft(d.getQuantityLeft() + r.getQuantity());
        if (d.getStatus() == MediStatus.FULLY_ALLOCATED) d.setStatus(MediStatus.LISTED);
    }

    private void rejectPending(MedicineDonation d, String why) {
        for (MedicineRequest r : requests.findByDonationOrderByCreatedAtDesc(d)) {
            if (r.getStatus() == MediRequestStatus.PENDING) {
                r.setStatus(MediRequestStatus.REJECTED);
                r.setDecisionNote(why);
                requests.save(r);
                notifications.notify(r.getRequester(), "\"" + d.getMedicineName() + "\": " + why);
            }
        }
    }

    private MedicineDonation find(Long id) {
        return donations.findById(id).orElseThrow(() -> ApiException.notFound("Item not found."));
    }

    private void log(User actor, String action, String targetType, Long targetId, String detail) {
        MediAudit a = new MediAudit();
        a.setActorId(actor.getId());
        a.setActorName(actor.getName());
        a.setActorRole(actor.getRole().key());
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setDetail(detail != null && detail.length() > 480 ? detail.substring(0, 480) : detail);
        audit.save(a);
    }

    /** Pickup address / phone only for the donor, admins and a requester whose request was approved. */
    private boolean canSeeContact(MedicineDonation d, User viewer) {
        if (viewer.getRole() == Role.ADMIN || d.getDonor().getId().equals(viewer.getId())) return true;
        return requests.findByDonationAndRequester(d, viewer)
                .map(r -> r.getStatus() == MediRequestStatus.APPROVED || r.getStatus() == MediRequestStatus.COLLECTED).orElse(false);
    }

    private MedicineDto dto(MedicineDonation d, User viewer) {
        boolean owner = d.getDonor().getId().equals(viewer.getId());
        boolean admin = viewer.getRole() == Role.ADMIN;
        boolean contact = canSeeContact(d, viewer);
        Long pending = owner ? requests.countByDonationAndStatus(d, MediRequestStatus.PENDING) : null;
        String mine = requests.findByDonationAndRequester(d, viewer).map(r -> r.getStatus().label()).orElse(null);
        return new MedicineDto(d.getId(), d.getCategory(), d.getMedicineName(), d.getStrength(), d.getDosageForm(),
                d.getBatchNumber(), d.getExpiryDate(), ChronoUnit.DAYS.between(LocalDate.now(), d.getExpiryDate()),
                d.getQuantity(), d.getQuantityLeft(), d.getUnit(), d.getStorage(), d.isSealedOriginalPack(),
                owner || admin ? d.getLicenceNumber() : null, d.getNotes(), d.getDonor().getName(), d.getCity(),
                contact ? d.getAddress() : null, contact ? d.getPhone() : null, d.getStatus().name(), d.getStatus().label(),
                owner || admin ? d.getReviewNote() : null, d.getCreatedAt(), pending, mine);
    }

    private MediRequestDto requestDto(MedicineRequest r, User viewer) {
        MedicineDonation d = r.getDonation();
        boolean viewerIsDonor = d.getDonor().getId().equals(viewer.getId());
        boolean shared = r.getStatus() == MediRequestStatus.APPROVED || r.getStatus() == MediRequestStatus.COLLECTED;
        User other = viewerIsDonor ? r.getRequester() : d.getDonor();
        return new MediRequestDto(r.getId(), d.getId(), d.getMedicineName(), r.getQuantity(), d.getUnit(), r.getPurpose(),
                r.getRegistrationNumber(), r.getStatus().label(), r.getDecisionNote(), other.getName(),
                shared ? (viewerIsDonor ? other.getPhone() : d.getPhone()) : null,
                shared && !viewerIsDonor ? d.getAddress() : null, r.getCreatedAt());
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
