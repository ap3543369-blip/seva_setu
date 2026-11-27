package com.foodshare.common;

import com.foodshare.bookshare.BookListing;
import com.foodshare.bookshare.BookListingRepository;
import com.foodshare.ecoai.*;
import com.foodshare.medishare.MedicineDonation;
import com.foodshare.medishare.MedicineDonationRepository;
import com.foodshare.medishare.MediStatus;
import com.foodshare.model.Role;
import com.foodshare.ngo.NgoProfile;
import com.foodshare.ngo.NgoProfileRepository;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Random;

/**
 * DEMO DATA for the new modules (BookShare, MediShare, EcoAI) and the new roles. Everything created here is marked
 * seeded = true where the model has that flag. Switch off with sevasetu.seed-demo=false. It only runs when the
 * FoodShare demo users exist (so it never takes over an empty database) and each part only when its table is empty.
 * Demo passwords are in the README; change or remove them before any real deployment.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "sevasetu.seed-demo", havingValue = "true", matchIfMissing = true)
public class SevaSetuSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SevaSetuSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final BookListingRepository books;
    private final MedicineDonationRepository medicines;
    private final EwasteRecordRepository records;
    private final EwastePickupRepository pickups;
    private final NgoProfileRepository ngoProfiles;

    public SevaSetuSeeder(UserRepository users, PasswordEncoder encoder, BookListingRepository books,
                          MedicineDonationRepository medicines, EwasteRecordRepository records, EwastePickupRepository pickups,
                          NgoProfileRepository ngoProfiles) {
        this.ngoProfiles = ngoProfiles;
        this.users = users;
        this.encoder = encoder;
        this.books = books;
        this.medicines = medicines;
        this.records = records;
        this.pickups = pickups;
    }

    /** Demo data must never stop the application from starting, so any problem is only logged. */
    @Override
    public void run(String... args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("SevaSetu demo data could not be created (the app still works without it): {}", e.toString());
        }
    }

    private void seed() {
        if (!users.existsByEmailIgnoreCase("donor@foodshare.com")) return; // FoodShare demo data not there: do nothing

        User rahul = user("Rahul Mehta", "rahul@foodshare.com", "donor123", Role.DONOR, "Household / Event", "9811100001", true);
        user("Asha Verma", "recipient@foodshare.com", "recipient123", Role.RECIPIENT, "Student", "9811100002", true);
        User hospital = user("City Care Hospital", "hospital@foodshare.com", "hospital123", Role.HOSPITAL, "Hospital", "9811100003", true);
        User recycler = user("GreenCycle Recyclers", "recycler@foodshare.com", "recycler123", Role.RECYCLER, "Recycler", "9811100004", true);

        // one NGO application waiting for the admin (demo: no certificate uploaded)
        User hope = user("Hope Foundation", "hope@foodshare.com", "hope123", Role.NGO, "NGO", "9811100005", false);
        if (ngoProfiles.findByUser(hope).isEmpty()) {
            NgoProfile p = new NgoProfile();
            p.setUser(hope);
            p.setRegistrationNumber("DEMO/2019/0457");
            p.setOrgKind("Trust");
            p.setYearEstablished(2019);
            p.setContactPerson("Meera Joshi");
            p.setContactPhone("9811100005");
            p.setAddress("21 Demo Lane, Lajpat Nagar");
            p.setCity("Delhi");
            p.setState("Delhi");
            p.setPincode("110024");
            p.setFocusAreas("Meals for daily-wage families, school supplies");
            p.setServiceAreas("South Delhi");
            p.setBeneficiariesServed(1200);
            ngoProfiles.save(p);
        }

        if (books.count() == 0) {
            book(rahul, "NCERT Mathematics", "NCERT", "Mathematics", "Class 10", "English", "Good", 3, "Clean copies, no torn pages.");
            book(rahul, "Concepts of Physics Vol. 1", "H. C. Verma", "Physics", "Class 11-12", "English", "Good", 1, "Light pencil marks in two chapters.");
            book(rahul, "Wings of Fire", "A. P. J. Abdul Kalam", "Literature", "General", "English", "Fair", 2, "Worn cover, readable.");
            book(rahul, "Science Textbook", "NCERT", "Science", "Class 8", "Hindi", "New", 5, "Unused set.");
            // one listing that still waits for the admin, so the Approvals page is not empty in a demo
            BookListing waiting = book(rahul, "Oxford English Grammar", "Oxford", "English", "Class 9", "English", "Good", 2, "Waiting for admin approval (demo).");
            waiting.setApproval(ApprovalStatus.PENDING);
            books.save(waiting);
        }
        if (medicines.count() == 0) {
            medicine(hospital, "First-aid supplies", "Sterile gauze & bandage packs", null, "Box", 40, "boxes", "Room temperature", LocalDate.now().plusDays(700), "GZ-2411");
            medicine(hospital, "Tablets/Capsules", "Paracetamol", "500 mg", "Tablet", 300, "strips", "Cool & dry place", LocalDate.now().plusDays(420), "PCM-7731");
        }
        if (records.count() == 0) seedEwaste(recycler);
        if (pickups.count() == 0) {
            pickup(rahul, null, "Laptops & computers", "Two old laptops and a charger", 6.5, PickupStatus.REQUESTED, null);
            pickup(rahul, null, "Batteries", "Used phone and power-bank batteries", 2.0, PickupStatus.REQUESTED, null);
            pickup(rahul, recycler, "Large appliances", "Old microwave oven", 14.0, PickupStatus.SCHEDULED, null);
            pickup(rahul, recycler, "Mobile phones", "Five unused phones", 1.5, PickupStatus.COLLECTED, 1.7);
        }
    }

    private User user(String name, String email, String password, Role role, String type, String phone, boolean verified) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setName(name);
            u.setEmail(email);
            u.setPasswordHash(encoder.encode(password));
            u.setRole(role);
            u.setOrgType(type);
            u.setPhone(phone);
            u.setVerified(verified);
            return users.save(u);
        });
    }

    private BookListing book(User donor, String title, String author, String subject, String level, String lang, String cond, int qty, String note) {
        BookListing b = new BookListing();
        b.setDonor(donor);
        b.setTitle(title);
        b.setAuthor(author);
        b.setSubject(subject);
        b.setLevel(level);
        b.setLanguage(lang);
        b.setBookCondition(cond);
        b.setQuantity(qty);
        b.setQuantityLeft(qty);
        b.setDescription(note);
        b.setCity("Delhi");
        b.setPincode("110005");
        b.setAddress("12 Demo Street, Karol Bagh");
        b.setPhone("9811100001");
        b.setSeeded(true);
        return books.save(b);
    }

    private void medicine(User hospital, String category, String name, String strength, String form, int qty, String unit,
                          String storage, LocalDate expiry, String batch) {
        MedicineDonation d = new MedicineDonation();
        d.setDonor(hospital);
        d.setCategory(category);
        d.setMedicineName(name);
        d.setStrength(strength);
        d.setDosageForm(form);
        d.setBatchNumber(batch);
        d.setExpiryDate(expiry);
        d.setQuantity(qty);
        d.setQuantityLeft(qty);
        d.setUnit(unit);
        d.setSealedOriginalPack(true);
        d.setStorage(storage);
        d.setLicenceNumber("DEMO-LIC-0001");
        d.setNotes("Demo listing for development.");
        d.setAddress("City Care Hospital, Pharmacy block");
        d.setCity("Delhi");
        d.setPhone("9811100003");
        d.setStatus(MediStatus.LISTED);
        d.setSeeded(true);
        medicines.save(d);
    }

    /** 30 months of SYNTHETIC history (trend + seasonality + noise) so the forecast page works in a demo. */
    private void seedEwaste(User recycler) {
        Random rnd = new Random(5);
        double[] base = {260, 180, 90, 140};
        List<String> cats = List.of("Mobile phones", "Laptops & computers", "Batteries", "Large appliances");
        for (int i = 29; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i + 1); // up to last month
            double t = 29 - i;
            for (int c = 0; c < cats.size(); c++) {
                double season = 1 + 0.15 * Math.sin(2 * Math.PI * ym.getMonthValue() / 12.0); // one shared yearly pattern, like real collection drives
                double kg = Math.max(5, (base[c] + 3.2 * t) * season + rnd.nextGaussian() * 9);
                EwasteRecord r = new EwasteRecord();
                r.setRecycler(recycler);
                r.setMonthKey(ym.toString());
                r.setCategory(cats.get(c));
                r.setKg(Math.round(kg * 10.0) / 10.0);
                r.setSeeded(true);
                records.save(r);
            }
        }
    }

    private void pickup(User donor, User recycler, String category, String desc, double est, PickupStatus status, Double actual) {
        EwastePickup p = new EwastePickup();
        p.setDonor(donor);
        p.setRecycler(recycler);
        p.setCategory(category);
        p.setDescription(desc);
        p.setEstimatedKg(est);
        p.setActualKg(actual);
        p.setAddress("12 Demo Street, Karol Bagh");
        p.setCity("Delhi");
        p.setPincode("110005");
        p.setPhone("9811100001");
        p.setPreferredDate(LocalDate.now().plusDays(3));
        if (status != PickupStatus.REQUESTED) p.setScheduledDate(LocalDate.now().plusDays(2));
        p.setStatus(status);
        p.setSeeded(true);
        pickups.save(p);
    }
}
