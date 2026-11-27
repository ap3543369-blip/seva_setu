package com.foodshare.ecoai;

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
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EcoService {

    public static final List<String> CATEGORIES = List.of("Mobile phones", "Laptops & computers", "Batteries", "Large appliances", "Small electronics", "Other");

    public record PickupForm(
            @NotBlank(message = "Choose a category.") String category,
            @Size(max = 500) String description,
            @Positive(message = "Enter the approximate weight in kg.") double estimatedKg,
            @NotBlank(message = "Pickup address is required.") String address,
            @NotBlank(message = "City is required.") String city,
            @Pattern(regexp = "\\d{6}", message = "Enter a 6-digit PIN code.") String pincode,
            @Pattern(regexp = "[6-9]\\d{9}", message = "Enter a valid 10-digit mobile number.") String phone,
            @NotNull(message = "Choose a preferred date.") LocalDate preferredDate) {
    }

    /** status: SCHEDULED (accept), COLLECTED (needs actualKg) or REQUESTED (release back to the open list) */
    public record PickupAction(@NotBlank String status, LocalDate scheduledDate, Double actualKg) {
    }

    public record RecordForm(@NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Month must look like 2026-03.") String month,
                             @NotBlank(message = "Choose a category.") String category,
                             @PositiveOrZero(message = "kg cannot be negative.") double kg) {
    }

    public record PickupDto(Long id, String category, String description, double estimatedKg, Double actualKg,
                            String address, String city, String phone, LocalDate preferredDate, LocalDate scheduledDate,
                            String status, String donor, String recycler, LocalDateTime createdAt) {
    }

    public record RecordDto(Long id, String month, String category, double kg, boolean demo) {
    }

    private final EwasteRecordRepository records;
    private final EwastePickupRepository pickups;
    private final UserRepository users;
    private final NotificationService notifications;
    private final EcoAiClient client;

    public EcoService(EwasteRecordRepository records, EwastePickupRepository pickups, UserRepository users,
                      NotificationService notifications, EcoAiClient client) {
        this.records = records;
        this.pickups = pickups;
        this.users = users;
        this.notifications = notifications;
        this.client = client;
    }

    // ---------------------------------------------------------------- donor
    @Transactional
    public PickupDto request(User donor, PickupForm f) {
        requireCategory(f.category());
        if (f.estimatedKg() < 0.1 || f.estimatedKg() > 5000) throw ApiException.badRequest("Estimated weight must be between 0.1 and 5000 kg.");
        if (f.preferredDate().isBefore(LocalDate.now())) throw ApiException.badRequest("Preferred date cannot be in the past.");
        EwastePickup p = new EwastePickup();
        p.setDonor(donor);
        p.setCategory(f.category());
        p.setDescription(blankToNull(f.description()));
        p.setEstimatedKg(f.estimatedKg());
        p.setAddress(f.address().trim());
        p.setCity(f.city().trim());
        p.setPincode(f.pincode());
        p.setPhone(f.phone());
        p.setPreferredDate(f.preferredDate());
        pickups.save(p);
        for (User r : users.findByRoleOrderByNameAsc(Role.RECYCLER)) {
            if (r.isVerified() && !r.isBlocked()) {
                notifications.notify(r, "New e-waste pickup request in " + p.getCity() + " (" + p.getCategory() + ", ~" + p.getEstimatedKg() + " kg).");
            }
        }
        return dto(p, donor);
    }

    @Transactional(readOnly = true)
    public List<PickupDto> mine(User donor) {
        return pickups.findByDonorOrderByCreatedAtDesc(donor).stream().map(p -> dto(p, donor)).toList();
    }

    @Transactional
    public PickupDto cancel(User donor, Long id) {
        EwastePickup p = find(id);
        if (!p.getDonor().getId().equals(donor.getId())) throw ApiException.forbidden("This is not your pickup.");
        if (p.getStatus() != PickupStatus.REQUESTED && p.getStatus() != PickupStatus.SCHEDULED) throw ApiException.conflict("This pickup can no longer be cancelled.");
        if (p.getRecycler() != null) notifications.notify(p.getRecycler(), p.getDonor().getName() + " cancelled the e-waste pickup in " + p.getCity() + ".");
        p.setStatus(PickupStatus.CANCELLED);
        return dto(pickups.save(p), donor);
    }

    // ---------------------------------------------------------------- recycler
    @Transactional(readOnly = true)
    public List<PickupDto> open(User recycler) {
        return pickups.findByStatusOrderByCreatedAtDesc(PickupStatus.REQUESTED).stream().map(p -> dto(p, recycler)).toList();
    }

    @Transactional(readOnly = true)
    public List<PickupDto> assigned(User recycler) {
        return pickups.findByRecyclerOrderByCreatedAtDesc(recycler).stream().map(p -> dto(p, recycler)).toList();
    }

    @Transactional
    public PickupDto act(User recycler, Long id, PickupAction a) {
        EwastePickup p = find(id);
        PickupStatus next;
        try {
            next = PickupStatus.valueOf(a.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status.");
        }
        boolean mine = p.getRecycler() != null && p.getRecycler().getId().equals(recycler.getId());
        if (p.getStatus() == PickupStatus.REQUESTED && next == PickupStatus.SCHEDULED) {
            LocalDate when = a.scheduledDate() != null ? a.scheduledDate() : p.getPreferredDate();
            if (when.isBefore(LocalDate.now())) throw ApiException.badRequest("Scheduled date cannot be in the past.");
            p.setRecycler(recycler);
            p.setScheduledDate(when);
            notifications.notify(p.getDonor(), recycler.getName() + " will collect your e-waste on " + when + ".");
        } else if (p.getStatus() == PickupStatus.SCHEDULED && mine && next == PickupStatus.REQUESTED) {
            p.setRecycler(null);
            p.setScheduledDate(null);
            notifications.notify(p.getDonor(), "Your e-waste pickup is open again for another recycler.");
        } else if (p.getStatus() == PickupStatus.SCHEDULED && mine && next == PickupStatus.COLLECTED) {
            Double kg = a.actualKg();
            if (kg == null || kg <= 0 || kg > 100000) throw ApiException.badRequest("Enter the collected weight in kg.");
            p.setActualKg(kg);
            addToMonth(recycler, YearMonth.now().toString(), p.getCategory(), kg); // real operations feed the forecast history
            notifications.notify(p.getDonor(), "Your e-waste was collected (" + kg + " kg). Thank you for recycling responsibly!");
        } else {
            throw ApiException.badRequest("This status change is not allowed.");
        }
        p.setStatus(next);
        return dto(pickups.save(p), recycler);
    }

    @Transactional
    public RecordDto saveRecord(User recycler, RecordForm f) {
        requireCategory(f.category());
        if (f.kg() > 1_000_000) throw ApiException.badRequest("kg is too large.");
        if (YearMonth.parse(f.month()).isAfter(YearMonth.now())) throw ApiException.badRequest("Month cannot be in the future.");
        EwasteRecord r = records.findByRecyclerAndMonthKeyAndCategory(recycler, f.month(), f.category()).orElseGet(EwasteRecord::new);
        r.setRecycler(recycler);
        r.setMonthKey(f.month());
        r.setCategory(f.category());
        r.setKg(f.kg()); // manual entry replaces the value for that month and category
        r.setSeeded(false);
        return recordDto(records.save(r));
    }

    @Transactional(readOnly = true)
    public List<RecordDto> records(User recycler) {
        return records.findByRecyclerOrderByMonthKeyDescCategoryAsc(recycler).stream().map(EcoService::recordDto).toList();
    }

    @Transactional
    public void deleteRecord(User recycler, Long id) {
        EwasteRecord r = records.findById(id).orElseThrow(() -> ApiException.notFound("Record not found."));
        if (!r.getRecycler().getId().equals(recycler.getId())) throw ApiException.forbidden("This is not your record.");
        records.delete(r);
    }

    // ---------------------------------------------------------------- forecast + dashboard
    /** Monthly totals (kg) for the user's scope: a recycler sees their own records, an admin sees the whole platform. */
    private List<EwasteRecord> scope(User u, String category) {
        List<EwasteRecord> list = u.getRole() == Role.ADMIN ? records.findAllByOrderByMonthKeyAsc()
                : records.findByRecyclerOrderByMonthKeyDescCategoryAsc(u);
        return list.stream().filter(r -> category == null || category.isBlank() || r.getCategory().equals(category)).toList();
    }

    private static TreeMap<String, Double> monthly(List<EwasteRecord> list) {
        TreeMap<String, Double> m = new TreeMap<>();
        for (EwasteRecord r : list) m.merge(r.getMonthKey(), r.getKg(), Double::sum);
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> forecast(User u, int horizon, String category, Double capacityKg) {
        if (horizon < 1 || horizon > 24) throw ApiException.badRequest("Horizon must be between 1 and 24 months.");
        if (category != null && !category.isBlank()) requireCategory(category);
        if (capacityKg != null && capacityKg <= 0) throw ApiException.badRequest("Pickup capacity must be greater than 0.");
        List<EwasteRecord> list = scope(u, category);
        TreeMap<String, Double> months = monthly(list);
        if (months.size() < 4) {
            throw ApiException.badRequest("At least 4 months of e-waste records are needed to forecast (you have " + months.size() + "). Add past months under Records.");
        }
        List<Map<String, Object>> history = new ArrayList<>();
        months.forEach((m, kg) -> history.add(Map.of("month", m, "kg", Math.round(kg * 10.0) / 10.0)));
        Map<String, Object> result = new LinkedHashMap<>(client.forecast(history, horizon, capacityKg));
        result.put("scope", u.getRole() == Role.ADMIN ? "platform" : "recycler");
        result.put("category", category == null || category.isBlank() ? "All categories" : category);
        result.put("history", history);
        result.put("demoData", list.stream().anyMatch(EwasteRecord::isSeeded));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(User u) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (u.getRole() == Role.DONOR) {
            List<EwastePickup> mine = pickups.findByDonorOrderByCreatedAtDesc(u);
            out.put("pickups", mine.size());
            out.put("collected", mine.stream().filter(p -> p.getStatus() == PickupStatus.COLLECTED).count());
            out.put("kgRecycled", round(mine.stream().filter(p -> p.getStatus() == PickupStatus.COLLECTED && p.getActualKg() != null).mapToDouble(EwastePickup::getActualKg).sum()));
            out.put("open", mine.stream().filter(p -> p.getStatus() == PickupStatus.REQUESTED || p.getStatus() == PickupStatus.SCHEDULED).count());
            return out;
        }
        List<EwasteRecord> list = scope(u, null);
        TreeMap<String, Double> months = monthly(list);
        List<String> keys = new ArrayList<>(months.keySet());
        List<String> last = keys.subList(Math.max(0, keys.size() - 12), keys.size());
        out.put("totalKg", round(list.stream().mapToDouble(EwasteRecord::getKg).sum()));
        out.put("months", last);
        out.put("series", last.stream().map(k -> round(months.get(k))).toList());
        out.put("byCategory", list.stream().collect(Collectors.groupingBy(EwasteRecord::getCategory, TreeMap::new, Collectors.summingDouble(EwasteRecord::getKg)))
                .entrySet().stream().map(e -> Map.of("category", e.getKey(), "kg", round(e.getValue()))).toList());
        List<EwastePickup> mine = u.getRole() == Role.ADMIN ? pickups.findAllByOrderByCreatedAtDesc() : pickups.findByRecyclerOrderByCreatedAtDesc(u);
        out.put("scheduled", mine.stream().filter(p -> p.getStatus() == PickupStatus.SCHEDULED).count());
        out.put("collected", mine.stream().filter(p -> p.getStatus() == PickupStatus.COLLECTED).count());
        out.put("openRequests", pickups.findByStatusOrderByCreatedAtDesc(PickupStatus.REQUESTED).size());
        out.put("monthsOfHistory", months.size());
        out.put("demoData", list.stream().anyMatch(EwasteRecord::isSeeded));
        return out;
    }

    // ---------------------------------------------------------------- helpers
    private void addToMonth(User recycler, String month, String category, double kg) {
        EwasteRecord r = records.findByRecyclerAndMonthKeyAndCategory(recycler, month, category).orElseGet(EwasteRecord::new);
        r.setRecycler(recycler);
        r.setMonthKey(month);
        r.setCategory(category);
        r.setKg(r.getKg() + kg);
        r.setSeeded(false);
        records.save(r);
    }

    private static void requireCategory(String c) {
        if (!CATEGORIES.contains(c)) throw ApiException.badRequest("Unknown category.");
    }

    private EwastePickup find(Long id) {
        return pickups.findById(id).orElseThrow(() -> ApiException.notFound("Pickup not found."));
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }
    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static RecordDto recordDto(EwasteRecord r) {
        return new RecordDto(r.getId(), r.getMonthKey(), r.getCategory(), r.getKg(), r.isSeeded());
    }

    /** Address and phone are shown to the donor, and to the recycler who accepted the pickup. Open requests show the area only. */
    private PickupDto dto(EwastePickup p, User viewer) {
        boolean owner = p.getDonor().getId().equals(viewer.getId());
        boolean assignedRecycler = p.getRecycler() != null && p.getRecycler().getId().equals(viewer.getId());
        boolean full = owner || assignedRecycler || viewer.getRole() == Role.ADMIN;
        return new PickupDto(p.getId(), p.getCategory(), p.getDescription(), p.getEstimatedKg(), p.getActualKg(),
                full ? p.getAddress() : null, p.getCity(), full ? p.getPhone() : null, p.getPreferredDate(), p.getScheduledDate(),
                p.getStatus().label(), p.getDonor().getName(), p.getRecycler() == null ? null : p.getRecycler().getName(), p.getCreatedAt());
    }
}
