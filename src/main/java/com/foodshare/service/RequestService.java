package com.foodshare.service;

import com.foodshare.dto.Dtos.CreateFoodRequest;
import com.foodshare.dto.Dtos.FoodRequestDto;
import com.foodshare.model.Donation;
import com.foodshare.model.DonationStatus;
import com.foodshare.model.FoodRequest;
import com.foodshare.model.NgoWeeklyStat;
import com.foodshare.model.RequestStatus;
import com.foodshare.model.User;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.FoodRequestRepository;
import com.foodshare.repo.NgoWeeklyStatRepository;
import com.foodshare.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class RequestService {

    private final FoodRequestRepository requests;
    private final DonationRepository donations;
    private final NgoWeeklyStatRepository weeklyStats;
    private final NotificationService notifications;

    public RequestService(FoodRequestRepository requests, DonationRepository donations,
                          NgoWeeklyStatRepository weeklyStats, NotificationService notifications) {
        this.notifications = notifications;
        this.requests = requests;
        this.donations = donations;
        this.weeklyStats = weeklyStats;
    }

    @Transactional
    public FoodRequestDto create(User ngo, CreateFoodRequest body) {
        if (!ngo.isVerified()) {
            throw ApiException.forbidden("Your NGO is waiting for admin verification before it can request food.");
        }
        Donation donation = donations.findById(body.donationId())
                .orElseThrow(() -> ApiException.notFound("That donation no longer exists."));
        if (donation.getStatus() != DonationStatus.PENDING || !donation.isApproved()) {
            throw ApiException.conflict("This donation is no longer available.");
        }
        if (requests.existsByDonationAndNgo(donation, ngo)) {
            throw ApiException.conflict("You have already requested this donation.");
        }
        FoodRequest r = new FoodRequest();
        r.setDonation(donation);
        r.setNgo(ngo);
        String note = body.note() == null ? "" : body.note().trim();
        r.setNote(note.isEmpty() ? null : note);
        r.setStatus(RequestStatus.PENDING);
        requests.save(r);
        notifications.notify(donation.getDonor(), ngo.getName() + " requested your " + donation.getFoodType() + ".");
        return Mapper.request(r);
    }

    @Transactional(readOnly = true)
    public List<FoodRequestDto> mine(User ngo) {
        return requests.findByNgoOrderByCreatedAtDesc(ngo).stream().map(Mapper::request).toList();
    }

    /** Pending -> Accepted -> Delivered. Accepting declines the other NGOs' pending requests. */
    @Transactional
    public FoodRequestDto updateStatus(User ngo, Long id, String statusText) {
        FoodRequest r = requests.findByIdAndNgo(id, ngo)
                .orElseThrow(() -> ApiException.notFound("Request not found."));
        RequestStatus target;
        try {
            target = RequestStatus.valueOf(statusText.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status.");
        }
        Donation donation = r.getDonation();

        if (target == RequestStatus.ACCEPTED && r.getStatus() == RequestStatus.PENDING) {
            if (donation.getStatus() != DonationStatus.PENDING) {
                throw ApiException.conflict("This donation was already accepted by another NGO.");
            }
            r.setStatus(RequestStatus.ACCEPTED);
            donation.setStatus(DonationStatus.ACCEPTED);
            donation.setNgo(ngo);
            for (FoodRequest other : requests.findByDonation(donation)) {
                if (!other.getId().equals(r.getId()) && other.getStatus() == RequestStatus.PENDING) {
                    other.setStatus(RequestStatus.DECLINED);
                    notifications.notify(other.getNgo(), "Your request for " + donation.getFoodType() + " was declined (another NGO was chosen).");
                }
            }
            notifications.notify(donation.getDonor(), ngo.getName() + " accepted your " + donation.getFoodType() + ". Please keep it ready for pickup.");
        } else if (target == RequestStatus.DELIVERED && r.getStatus() == RequestStatus.ACCEPTED) {
            r.setStatus(RequestStatus.DELIVERED);
            donation.setStatus(DonationStatus.DELIVERED);
            addToWeeklyChart(ngo, donation.getMeals());
            notifications.notify(donation.getDonor(), "Your " + donation.getFoodType() + " was delivered by " + ngo.getName() + ". Thank you!");
        } else {
            throw ApiException.conflict("A " + r.getStatus().label().toLowerCase()
                    + " request cannot be changed to " + target.label().toLowerCase() + ".");
        }
        return Mapper.request(r);
    }

    private void addToWeeklyChart(User ngo, int meals) {
        LocalDate today = LocalDate.now();
        String month = YearMonth.from(today).toString();
        int week = Math.min(4, (today.getDayOfMonth() - 1) / 7 + 1);
        NgoWeeklyStat stat = weeklyStats.findByNgoAndMonthKeyAndWeekNo(ngo, month, week)
                .orElseGet(() -> new NgoWeeklyStat(ngo, month, week, 0));
        stat.setMeals(stat.getMeals() + meals);
        weeklyStats.save(stat);
    }
}
