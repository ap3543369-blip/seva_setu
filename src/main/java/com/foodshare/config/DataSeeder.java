package com.foodshare.config;

import com.foodshare.model.*;
import com.foodshare.repo.*;
import com.foodshare.service.DonationService;
import com.foodshare.service.RewardService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

/**
 * Fills an empty database with demo data (same sample content as the original static prototype).
 * Dates are relative to "today", so the demo never looks stale.
 *
 * Demo logins:  admin@foodshare.com / admin123   (Admin)
 *               donor@foodshare.com / donor123   (Spice Kitchen)
 *               ngo@foodshare.com   / ngo123     (Robin Hood Army)
 */
@Component
@org.springframework.core.annotation.Order(1) // must run before SevaSetuSeeder (it needs the FoodShare demo users)
public class DataSeeder implements CommandLineRunner {

    private static final int DEMO_POINTS = RewardService.DEMO_POINTS;

    private final UserRepository users;
    private final DonationRepository donations;
    private final FoodRequestRepository requests;
    private final RewardOfferRepository offers;
    private final RedeemedRewardRepository redeemed;
    private final NgoWeeklyStatRepository weeklyStats;
    private final ImpactSnapshotRepository snapshots;
    private final PasswordEncoder encoder;
    private final NotificationRepository notifications;
    private final StoryRepository stories;

    public DataSeeder(UserRepository users, DonationRepository donations, FoodRequestRepository requests,
                      RewardOfferRepository offers, RedeemedRewardRepository redeemed,
                      NgoWeeklyStatRepository weeklyStats, ImpactSnapshotRepository snapshots,
                      PasswordEncoder encoder, NotificationRepository notifications,
                      StoryRepository stories) {
        this.stories = stories;
        this.notifications = notifications;
        this.users = users;
        this.donations = donations;
        this.requests = requests;
        this.offers = offers;
        this.redeemed = redeemed;
        this.weeklyStats = weeklyStats;
        this.snapshots = snapshots;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        seedRewardsAndImpact();

        String donorHash = encoder.encode("donor123");
        String ngoHash = encoder.encode("ngo123");

        User spice = user("Spice Kitchen", "donor@foodshare.com", donorHash, Role.DONOR, "Restaurant / Canteen",
                "+91 9876543210", DEMO_POINTS);
        User greenLeaf = user("Green Leaf Canteen", "greenleaf@foodshare.com", donorHash, Role.DONOR, "Restaurant / Canteen", "+91 9810000001", 0);
        User sunrise = user("Sunrise Bakery", "sunrise@foodshare.com", donorHash, Role.DONOR, "Hotel / Caterer", "+91 9810000002", 0);
        User cityMall = user("City Mall Canteen", "citymall@foodshare.com", donorHash, Role.DONOR, "Restaurant / Canteen", "+91 9810000003", 0);

        user("FoodShare Admin", "admin@foodshare.com", encoder.encode("admin123"), Role.ADMIN, "Admin", "+91 9810000000", 0);

        User robin = user("Robin Hood Army", "ngo@foodshare.com", ngoHash, Role.NGO, "NGO", "+91 9810000010", 0);
        User feeding = user("Feeding India", "feedingindia@foodshare.com", ngoHash, Role.NGO, "NGO", "+91 9810000011", 0);
        User akshaya = user("Akshaya Patra", "akshayapatra@foodshare.com", ngoHash, Role.NGO, "NGO", "+91 9810000012", 0);
        User goonj = user("Goonj", "goonj@foodshare.com", ngoHash, Role.NGO, "NGO", "+91 9810000013", 0);

        // ---- Spice Kitchen: past, delivered donations (shown in "Donation History") ----
        history(spice, robin, "Cooked Food", "Cooked", "veg-biryani", 20, "kg", 2, 100);
        history(spice, feeding, "Fruits & Vegetables", "Vegetables", "mixed-vegetables", 15, "kg", 4, 80);
        history(spice, akshaya, "Packed Food", "Packed", "bread-packets", 10, "kg", 6, 60);
        history(spice, goonj, "Fruits & Vegetables", "Fruits", "fruits", 25, "kg", 9, 120);

        // ---- Live donations NGOs can see on the Request Food page ----
        LocalDate today = LocalDate.now();
        Donation biryaniOpen = open(spice, "Cooked Food", "Cooked", "veg-biryani", 20, "kg", "Karol Bagh", 2,
                today.plusDays(5), LocalTime.of(14, 0), LocalTime.of(18, 0));
        Donation vegOpen = open(greenLeaf, "Fruits & Vegetables", "Vegetables", "mixed-vegetables", 15, "kg",
                "Rajouri Garden", 5, today.plusDays(5), LocalTime.of(16, 0), LocalTime.of(19, 0));
        Donation breadOpen = open(sunrise, "Bakery Items", "Bakery", "bread-packets", 10, "kg", "Lajpat Nagar", 3,
                today.plusDays(5), LocalTime.of(13, 0), LocalTime.of(16, 0));
        Donation fruitsOpen = open(cityMall, "Fruits & Vegetables", "Fruits", "fruits", 25, "kg", "Saket", 6,
                today.plusDays(5), LocalTime.of(17, 0), LocalTime.of(20, 0));

        // ---- Robin Hood Army's own requests (NGO dashboard) ----
        request(robin, vegOpen, RequestStatus.PENDING, "We can collect by 6 PM.");
        Donation biryaniAccepted = open(spice, "Cooked Food", "Cooked", "veg-biryani", 20, "kg", "Karol Bagh", 2,
                today.plusDays(1), LocalTime.of(14, 0), LocalTime.of(17, 0));
        biryaniAccepted.setStatus(DonationStatus.ACCEPTED);
        biryaniAccepted.setNgo(robin);
        donations.save(biryaniAccepted);
        request(robin, biryaniAccepted, RequestStatus.ACCEPTED, null);

        Donation breadAccepted = open(sunrise, "Bakery Items", "Bakery", "bread-packets", 10, "kg", "Lajpat Nagar", 3,
                today.plusDays(1), LocalTime.of(13, 0), LocalTime.of(16, 0));
        breadAccepted.setStatus(DonationStatus.ACCEPTED);
        breadAccepted.setNgo(robin);
        donations.save(breadAccepted);
        request(robin, breadAccepted, RequestStatus.ACCEPTED, null);

        Donation fruitsDelivered = open(cityMall, "Fruits & Vegetables", "Fruits", "fruits", 25, "kg", "Saket", 6,
                today.minusDays(2), LocalTime.of(17, 0), LocalTime.of(20, 0));
        fruitsDelivered.setStatus(DonationStatus.DELIVERED);
        fruitsDelivered.setNgo(robin);
        fruitsDelivered.setCreatedAt(LocalDateTime.now().minusDays(5));
        donations.save(fruitsDelivered);
        request(robin, fruitsDelivered, RequestStatus.DELIVERED, null);

        notifications.save(new Notification(spice, "Robin Hood Army accepted your Veg Biryani. Please keep it ready for pickup."));
        notifications.save(new Notification(spice, "Your Fruits donation was delivered. Thank you!"));
        notifications.save(new Notification(robin, "Welcome to FoodShare! Browse available food and send your first request."));

        setNgoTotals(feeding, 64, 1800, 12);
        setNgoTotals(akshaya, 210, 5200, 30);
        setNgoTotals(goonj, 45, 950, 9);

        stories.save(new Story("Ritu Sharma", "NGO Partner",
                "FoodShare has helped us provide fresh meals to hundreds of families every month. The reward system also motivates more organisations to donate.", "ritu-sharma"));
        stories.save(new Story("Anil Kumar", "Green Leaf Canteen",
                "We used to throw away trays of food every night. Now it reaches people within the hour.", "anil-kumar"));
        stories.save(new Story("Meera Patel", "Hostel Mess Coordinator",
                "Posting a donation takes under a minute, and the pickup is always on time.", "meera-patel"));

        // ---- Past redeemed rewards ("Reward History") ----
        reward(spice, "Zomato", "zomato", 500, 2000, "FS-ZO5K-91AB", 17);
        reward(spice, "Flipkart", "flipkart", 250, 1000, "FS-FK2H-77QD", 53);
        reward(spice, "Amazon", "amazon", 100, 500, "FS-AM1X-30LM", 97);

        // ---- Chart data for Robin Hood Army (this month and the two before) ----
        YearMonth now = YearMonth.now();
        weeks(robin, now, 420, 610, 380, 720);
        weeks(robin, now.minusMonths(1), 350, 480, 540, 610);
        weeks(robin, now.minusMonths(2), 300, 410, 390, 450);

        // ---- Totals "from before FoodShare moved to this system", so the demo accounts match the original numbers ----
        spice.setLegacyDonations(48 - (int) donations.countByDonor(spice));
        spice.setLegacyMeals(1250 - (int) donations.sumMealsByDonor(spice));
        spice.setLegacyCo2(320 - (int) Math.round(donations.sumMealsByDonor(spice) * 0.25));
        users.save(spice);

        robin.setLegacyRequests(120 - (int) requests.countByNgo(robin));
        robin.setLegacyFulfilled(85 - (int) requests.countByNgoAndStatus(robin, RequestStatus.DELIVERED));
        robin.setLegacyMealsReceived(2300 - (int) requests.sumDeliveredMeals(robin));
        robin.setLegacyPartners(15 - (int) requests.countPartnerDonors(robin));
        users.save(robin);
    }

    // ------------------------------------------------------------------ helpers

    private void setNgoTotals(User ngo, int fulfilled, int meals, int partners) {
        ngo.setLegacyFulfilled(fulfilled);
        ngo.setLegacyRequests(fulfilled + 5);
        ngo.setLegacyMealsReceived(meals);
        ngo.setLegacyPartners(partners);
        users.save(ngo);
    }

    private User user(String name, String email, String hash, Role role, String orgType, String phone, int points) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(hash);
        u.setRole(role);
        u.setOrgType(orgType);
        u.setPhone(phone);
        u.setPoints(points);
        return users.save(u);
    }

    private Donation open(User donor, String foodType, String category, String imageKey, double qty, String unit,
                          String area, double km, LocalDate pickup, LocalTime from, LocalTime to) {
        Donation d = new Donation();
        d.setDonor(donor);
        d.setFoodType(foodType);
        d.setCategory(category);
        d.setImageKey(imageKey);
        d.setQuantity(qty);
        d.setUnit(unit);
        d.setDescription("Freshly prepared and packed in clean containers.");
        d.setFoodCondition("Fresh (Ready to eat)");
        d.setBestDate(pickup);
        d.setBestTime(LocalTime.of(22, 0));
        d.setAddress("Main Market, " + area);
        d.setCity("Delhi");
        d.setArea(area);
        d.setPincode("110005");
        d.setPhone(donor.getPhone() == null ? "9876543210" : donor.getPhone().replaceAll("\\D", "").replaceFirst("^91", ""));
        d.setHandover("NGO pickup");
        d.setPickupDate(pickup);
        d.setTimeFrom(from);
        d.setTimeTo(to);
        d.setMeals(DonationService.mealsFor(qty, unit));
        d.setPoints(DonationService.pointsFor(qty));
        d.setDistanceKm(km);
        d.setStatus(DonationStatus.PENDING);
        d.setSeeded(true);
        d.setCreatedAt(LocalDateTime.now().minusHours(3));
        return donations.save(d);
    }

    private void history(User donor, User ngo, String foodType, String category, String imageKey, double qty,
                         String unit, int daysAgo, int points) {
        Donation d = open(donor, foodType, category, imageKey, qty, unit, "Karol Bagh", 2,
                LocalDate.now().minusDays(daysAgo), LocalTime.of(12, 0), LocalTime.of(15, 0));
        d.setStatus(DonationStatus.DELIVERED);
        d.setNgo(ngo);
        d.setPoints(points);
        d.setCreatedAt(LocalDate.now().minusDays(daysAgo).atTime(12, 30));
        donations.save(d);
    }

    private void request(User ngo, Donation donation, RequestStatus status, String note) {
        FoodRequest r = new FoodRequest();
        r.setNgo(ngo);
        r.setDonation(donation);
        r.setStatus(status);
        r.setNote(note);
        requests.save(r);
    }

    private void reward(User user, String brand, String logo, int amount, int cost, String code, int daysAgo) {
        RedeemedReward r = new RedeemedReward();
        r.setUser(user);
        r.setBrand(brand);
        r.setLogo(logo);
        r.setAmount(amount);
        r.setCost(cost);
        r.setCode(code);
        r.setRedeemedAt(LocalDateTime.now().minusDays(daysAgo));
        r.setSeeded(true);
        redeemed.save(r);
    }

    private void weeks(User ngo, YearMonth month, int w1, int w2, int w3, int w4) {
        int[] values = {w1, w2, w3, w4};
        for (int i = 0; i < 4; i++) {
            weeklyStats.save(new NgoWeeklyStat(ngo, month.toString(), i + 1, values[i]));
        }
    }

    private void seedRewardsAndImpact() {
        offers.save(new RewardOffer("amz100", "Amazon", "amazon", 100, 500));
        offers.save(new RewardOffer("fk250", "Flipkart", "flipkart", 250, 1000));
        offers.save(new RewardOffer("sw500", "Swiggy", "swiggy", 500, 2000));
        offers.save(new RewardOffer("zo500", "Zomato", "zomato", 500, 2000));
        offers.save(new RewardOffer("amz1000", "Amazon", "amazon", 1000, 3500));
        offers.save(new RewardOffer("bb1000", "BigBasket", "bigbasket", 1000, 3500));

        snapshots.save(new ImpactSnapshot("month", 500, 360, 15, 42, 250,
                "W1,W2,W3,W4", "100,120,130,150", ""));
        snapshots.save(new ImpactSnapshot("year", 3650, 2600, 105, 210, 250,
                "Jan,Feb,Mar,Apr,May,Jun,Jul,Aug,Sep,Oct", "210,260,300,330,360,390,410,430,460,500", ""));
        snapshots.save(new ImpactSnapshot("all", 12450, 8900, 350, 500, 250,
                "2022,2023,2024,2025,2026", "800,1700,2900,3400,3650", "Since 2022"));
    }
}
