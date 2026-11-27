package com.foodshare.service;

import com.foodshare.dto.Dtos.DonationDto;
import com.foodshare.dto.Dtos.DonorDashboard;
import com.foodshare.dto.Dtos.NgoChart;
import com.foodshare.dto.Dtos.NgoDashboard;
import com.foodshare.model.DonationStatus;
import com.foodshare.model.NgoWeeklyStat;
import com.foodshare.model.RequestStatus;
import com.foodshare.model.User;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.FoodRequestRepository;
import com.foodshare.repo.NgoWeeklyStatRepository;
import com.foodshare.repo.RedeemedRewardRepository;
import com.foodshare.repo.UserRepository;
import com.foodshare.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DashboardService {

    /** Donor reward milestone: total donations needed to unlock the next reward. */
    static final int MILESTONE = 50;

    private final UserRepository users;
    private final DonationRepository donations;
    private final FoodRequestRepository requests;
    private final RedeemedRewardRepository redeemed;
    private final NgoWeeklyStatRepository weeklyStats;

    public DashboardService(UserRepository users, DonationRepository donations, FoodRequestRepository requests,
                            RedeemedRewardRepository redeemed, NgoWeeklyStatRepository weeklyStats) {
        this.users = users;
        this.donations = donations;
        this.requests = requests;
        this.redeemed = redeemed;
        this.weeklyStats = weeklyStats;
    }

    @Transactional(readOnly = true)
    public DonorDashboard donor(User donor) {
        User u = users.findById(donor.getId()).orElseThrow();
        long total = u.getLegacyDonations() + donations.countByDonorAndStatusNot(u, DonationStatus.CANCELLED);
        long dbMeals = donations.sumMealsByDonor(u);
        long meals = u.getLegacyMeals() + dbMeals;
        long co2 = u.getLegacyCo2() + Math.round(dbMeals * 0.25);
        long gifts = redeemed.countByUser(u);
        List<DonationDto> recent = donations.findByDonorOrderByCreatedAtDesc(u).stream().limit(3)
                .map(Mapper::donationFull).toList();
        return new DonorDashboard(u.getName(), total, meals, co2, gifts, u.getPoints(), MILESTONE, recent);
    }

    @Transactional(readOnly = true)
    public NgoDashboard ngo(User ngo) {
        User u = users.findById(ngo.getId()).orElseThrow();
        return new NgoDashboard(u.getName(),
                u.getLegacyRequests() + requests.countByNgo(u),
                u.getLegacyFulfilled() + requests.countByNgoAndStatus(u, RequestStatus.DELIVERED),
                u.getLegacyMealsReceived() + requests.sumDeliveredMeals(u),
                u.getLegacyPartners() + requests.countPartnerDonors(u));
    }

    /** Public NGO directory: verified, active NGOs with their totals. */
    @Transactional(readOnly = true)
    public List<com.foodshare.dto.Dtos.NgoDto> ngoDirectory() {
        return users.findByRoleOrderByNameAsc(com.foodshare.model.Role.NGO).stream()
                .filter(User::isVerified).filter(u -> !u.isBlocked())
                .map(u -> new com.foodshare.dto.Dtos.NgoDto(u.getId(), u.getName(), u.getOrgType(),
                        u.getLegacyFulfilled() + requests.countByNgoAndStatus(u, RequestStatus.DELIVERED),
                        u.getLegacyMealsReceived() + requests.sumDeliveredMeals(u),
                        u.getLegacyPartners() + requests.countPartnerDonors(u)))
                .toList();
    }

    /** Meals received in weeks 1-4 of the given month (yyyy-MM, default: this month). */
    @Transactional(readOnly = true)
    public NgoChart chart(User ngo, String month) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Month must look like 2026-10.");
        }
        Integer[] weeks = {0, 0, 0, 0};
        for (NgoWeeklyStat s : weeklyStats.findByNgoAndMonthKey(ngo, ym.toString())) {
            if (s.getWeekNo() >= 1 && s.getWeekNo() <= 4) {
                weeks[s.getWeekNo() - 1] += s.getMeals();
            }
        }
        return new NgoChart(ym.toString(), new ArrayList<>(Arrays.asList(weeks)));
    }
}
