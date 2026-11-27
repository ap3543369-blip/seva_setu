package com.foodshare.ngo;

import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.AuthService;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** NGO registration: an NGO sends its details + certificate, an admin reviews them, only then the NGO is verified. */
@Service
public class NgoService {

    public static final List<String> ORG_KINDS = List.of("Trust", "Society", "Section 8 company", "Registered charity", "Other");
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    /** email + password are only used for the first registration; everything else is also used when re-submitting. */
    public record NgoForm(
            String email,
            @Size(max = 100) String password,
            @NotBlank(message = "Enter the organisation name.") @Size(min = 3, max = 120, message = "Name must be 3 to 120 characters.") String orgName,
            @NotBlank(message = "Choose the organisation type.") String orgKind,
            @NotBlank(message = "Enter your registration number.") @Pattern(regexp = "[A-Za-z0-9/\\-_. ]{3,40}", message = "Registration number: 3-40 letters, digits, / - _ .") String registrationNumber,
            @Min(value = 1800, message = "Enter a valid year.") @Max(value = 2100, message = "Enter a valid year.") int yearEstablished,
            @NotBlank(message = "Enter the contact person's name.") @Size(max = 80) String contactPerson,
            @NotBlank(message = "Enter a mobile number.") @Pattern(regexp = "[6-9]\\d{9}", message = "Enter a valid 10-digit mobile number.") String contactPhone,
            @NotBlank(message = "Enter the office address.") @Size(max = 300) String address,
            @NotBlank(message = "Enter the city.") @Size(max = 80) String city,
            @NotBlank(message = "Enter the state.") @Size(max = 80) String state,
            @NotBlank(message = "Enter the PIN code.") @Pattern(regexp = "\\d{6}", message = "Enter a 6-digit PIN code.") String pincode,
            @Pattern(regexp = "^$|https?://\\S{3,200}", message = "Website must start with http:// or https://") String website,
            @NotBlank(message = "Describe what your NGO works on.") @Size(max = 500) String focusAreas,
            @Size(max = 300) String serviceAreas,
            @Min(value = 0, message = "Cannot be negative.") @Max(100000000) long beneficiariesServed,
            boolean declaration) {
    }

    public record ReviewBody(@NotBlank String decision, @Size(max = 500) String note) {
    }

    public record NgoApplicationDto(Long id, Long userId, String orgName, String email, String registrationNumber, String orgKind,
                                    int yearEstablished, String contactPerson, String contactPhone, String address, String city,
                                    String state, String pincode, String website, String focusAreas, String serviceAreas,
                                    long beneficiariesServed, String status, String statusLabel, String reviewNote,
                                    LocalDateTime submittedAt, LocalDateTime reviewedAt, boolean hasDocument) {
    }

    private final NgoProfileRepository profiles;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final NgoDocumentStorage storage;
    private final NotificationService notifications;
    private final AuthService auth;

    public NgoService(NgoProfileRepository profiles, UserRepository users, PasswordEncoder encoder,
                      NgoDocumentStorage storage, NotificationService notifications, AuthService auth) {
        this.profiles = profiles;
        this.users = users;
        this.encoder = encoder;
        this.storage = storage;
        this.notifications = notifications;
        this.auth = auth;
    }

    // ---------------------------------------------------------------- NGO side
    @Transactional
    public NgoApplicationDto register(NgoForm f, MultipartFile certificate, HttpServletRequest request) {
        String email = f.email() == null ? "" : f.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) throw ApiException.badRequest("Enter a valid email address.");
        if (f.password() == null || f.password().length() < 6) throw ApiException.badRequest("Password must be at least 6 characters.");
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this email already exists.");
        checkDetails(f, null);
        String file = storage.store(certificate);
        try {
            User u = new User();
            u.setName(f.orgName().trim());
            u.setEmail(email);
            u.setPasswordHash(encoder.encode(f.password()));
            u.setRole(Role.NGO);
            u.setOrgType("NGO");
            u.setPhone(f.contactPhone());
            u.setPoints(0);
            u.setVerified(false); // an admin must approve the application first
            users.save(u);
            NgoProfile p = new NgoProfile();
            p.setUser(u);
            apply(p, f);
            p.setDocumentFile(file);
            profiles.save(p);
            notifyAdmins("New NGO application from " + u.getName() + " (reg. no. " + p.getRegistrationNumber() + ") is waiting for verification.");
            auth.startSession(request, u);
            return dto(p);
        } catch (RuntimeException e) {
            storage.delete(file);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Optional<NgoApplicationDto> mine(User ngo) {
        return profiles.findByUser(ngo).map(NgoService::dto);
    }

    /** Edit and re-send the application (after "changes needed", or while it is still under review). */
    @Transactional
    public NgoApplicationDto resubmit(User ngo, NgoForm f, MultipartFile certificate) {
        if (ngo.getRole() != Role.NGO) throw ApiException.forbidden("Only NGO accounts can do this.");
        Optional<NgoProfile> existing = profiles.findByUser(ngo);
        if (existing.isPresent() && existing.get().getStatus() == NgoApplicationStatus.APPROVED) {
            throw ApiException.conflict("Your NGO is already approved.");
        }
        checkDetails(f, existing.orElse(null));
        NgoProfile p = existing.orElseGet(NgoProfile::new);
        String oldFile = p.getDocumentFile();
        boolean newFile = certificate != null && !certificate.isEmpty();
        if (existing.isEmpty() && !newFile) throw ApiException.badRequest("Upload your registration certificate (PDF, PNG or JPG).");
        if (newFile) p.setDocumentFile(storage.store(certificate));
        p.setUser(ngo);
        apply(p, f);
        p.setStatus(NgoApplicationStatus.SUBMITTED);
        p.setReviewNote(null);
        p.setSubmittedAt(LocalDateTime.now());
        profiles.save(p);
        User managed = users.findById(ngo.getId()).orElse(ngo);
        managed.setName(f.orgName().trim());
        managed.setPhone(f.contactPhone());
        users.save(managed);
        if (newFile) storage.delete(oldFile);
        notifyAdmins(managed.getName() + " re-submitted its NGO application for review.");
        return dto(p);
    }

    @Transactional(readOnly = true)
    public Resource myDocument(User ngo) {
        NgoProfile p = profiles.findByUser(ngo).orElseThrow(() -> ApiException.notFound("No application found."));
        return storage.load(p.getDocumentFile());
    }

    // ---------------------------------------------------------------- admin side
    @Transactional(readOnly = true)
    public List<NgoApplicationDto> list(String status) {
        String s = status == null ? "all" : status.trim().toLowerCase(Locale.ROOT);
        return profiles.findAllByOrderBySubmittedAtDesc().stream()
                .filter(p -> s.equals("all") || p.getStatus().name().toLowerCase(Locale.ROOT).equals(s))
                .map(NgoService::dto).toList();
    }

    @Transactional(readOnly = true)
    public NgoApplicationDto detail(Long id) {
        return dto(find(id));
    }

    @Transactional(readOnly = true)
    public Resource document(Long id) {
        return storage.load(find(id).getDocumentFile());
    }

    @Transactional
    public NgoApplicationDto review(User admin, Long id, ReviewBody b) {
        NgoProfile p = find(id);
        String decision = b.decision().trim().toLowerCase(Locale.ROOT);
        String note = b.note() == null || b.note().isBlank() ? null : b.note().trim();
        if (p.getStatus() != NgoApplicationStatus.SUBMITTED) throw ApiException.conflict("This application was already reviewed.");
        User ngo = users.findById(p.getUser().getId()).orElseThrow();
        switch (decision) {
            case "approve" -> {
                p.setStatus(NgoApplicationStatus.APPROVED);
                ngo.setVerified(true);
                notifications.notify(ngo, "Your NGO has been verified. You can now use SevaSetu.");
            }
            case "reject" -> {
                if (note == null) throw ApiException.badRequest("Please write what the NGO needs to fix.");
                p.setStatus(NgoApplicationStatus.REJECTED);
                ngo.setVerified(false);
                notifications.notify(ngo, "Your NGO application needs changes: " + note);
            }
            default -> throw ApiException.badRequest("Decision must be approve or reject.");
        }
        p.setReviewNote(note);
        p.setReviewedAt(LocalDateTime.now());
        users.save(ngo);
        return dto(profiles.save(p));
    }

    // ---------------------------------------------------------------- helpers
    private void checkDetails(NgoForm f, NgoProfile current) {
        if (!f.declaration()) throw ApiException.badRequest("Please confirm that the details are correct and you are authorised to register this NGO.");
        if (!ORG_KINDS.contains(f.orgKind())) throw ApiException.badRequest("Choose a valid organisation type.");
        if (f.yearEstablished() > Year.now().getValue()) throw ApiException.badRequest("Year established cannot be in the future.");
        String reg = f.registrationNumber().trim();
        boolean sameAsOwn = current != null && current.getRegistrationNumber() != null && current.getRegistrationNumber().equalsIgnoreCase(reg);
        if (!sameAsOwn && profiles.existsByRegistrationNumberIgnoreCase(reg)) {
            throw ApiException.conflict("An NGO with this registration number is already registered.");
        }
    }

    private static void apply(NgoProfile p, NgoForm f) {
        p.setRegistrationNumber(f.registrationNumber().trim());
        p.setOrgKind(f.orgKind());
        p.setYearEstablished(f.yearEstablished());
        p.setContactPerson(f.contactPerson().trim());
        p.setContactPhone(f.contactPhone());
        p.setAddress(f.address().trim());
        p.setCity(f.city().trim());
        p.setState(f.state().trim());
        p.setPincode(f.pincode());
        p.setWebsite(f.website() == null || f.website().isBlank() ? null : f.website().trim());
        p.setFocusAreas(f.focusAreas().trim());
        p.setServiceAreas(f.serviceAreas() == null || f.serviceAreas().isBlank() ? null : f.serviceAreas().trim());
        p.setBeneficiariesServed(f.beneficiariesServed());
    }

    private NgoProfile find(Long id) {
        return profiles.findById(id).orElseThrow(() -> ApiException.notFound("Application not found."));
    }

    private void notifyAdmins(String message) {
        for (User a : users.findByRoleOrderByNameAsc(Role.ADMIN)) notifications.notify(a, message);
    }

    static NgoApplicationDto dto(NgoProfile p) {
        User u = p.getUser();
        return new NgoApplicationDto(p.getId(), u.getId(), u.getName(), u.getEmail(), p.getRegistrationNumber(), p.getOrgKind(),
                p.getYearEstablished(), p.getContactPerson(), p.getContactPhone(), p.getAddress(), p.getCity(), p.getState(),
                p.getPincode(), p.getWebsite(), p.getFocusAreas(), p.getServiceAreas(), p.getBeneficiariesServed(),
                p.getStatus().name(), p.getStatus().label(), p.getReviewNote(), p.getSubmittedAt(), p.getReviewedAt(),
                p.getDocumentFile() != null);
    }
}
