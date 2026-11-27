package com.foodshare.service;

import com.foodshare.dto.Dtos.DonationDto;
import com.foodshare.dto.Dtos.FoodRequestDto;
import com.foodshare.dto.Dtos.UserDto;
import com.foodshare.model.Donation;
import com.foodshare.model.FoodRequest;
import com.foodshare.model.User;

import java.time.LocalDateTime;
import java.util.List;

/** Entity -> DTO conversion helpers. */
public final class Mapper {

    private Mapper() {
    }

    public static UserDto user(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().key(), u.getOrgType(),
                u.getPhone(), u.getPoints(), u.isVerified());
    }

    /** "20 kg", "12.5 litres" */
    static String qty(double quantity, String unit) {
        String n = quantity == Math.rint(quantity) ? String.valueOf((long) quantity) : String.valueOf(quantity);
        return n + " " + unit;
    }

    static LocalDateTime pickupAt(Donation d) {
        if (d.getPickupDate() == null) {
            return null;
        }
        return d.getPickupDate().atTime(d.getTimeFrom() == null ? java.time.LocalTime.NOON : d.getTimeFrom());
    }

    static List<String> imageUrls(Donation d) {
        return d.getImageFiles().stream().map(f -> "/uploads/" + f).toList();
    }

    /** Public-safe view: only the area is shown, no street address or phone. */
    static DonationDto donation(Donation d, boolean requested) {
        return build(d, requested, false);
    }

    /** Owner / admin / accepted-NGO view: includes the pickup address and contact phone. */
    static DonationDto donationFull(Donation d) {
        return build(d, false, true);
    }

    /** "Awaiting approval" / "Rejected" while the admin has not approved the donation, otherwise the normal status. */
    private static String statusLabel(Donation d) {
        return d.isApproved() ? d.getStatus().label() : d.getApproval().label();
    }

    private static DonationDto build(Donation d, boolean requested, boolean full) {
        return new DonationDto(d.getId(), d.getFoodType(), d.getCategory(), qty(d.getQuantity(), d.getUnit()),
                d.getQuantity(), d.getUnit(), d.getMeals(), d.getPoints(), statusLabel(d),
                d.getNgo() == null ? null : d.getNgo().getName(), d.getDonor().getName(), d.getArea(),
                full ? d.getAddress() + ", " + d.getCity() + " " + d.getPincode() : d.getArea(),
                d.getDistanceKm(), pickupAt(d), d.getFoodCondition(), d.getCreatedAt(),
                d.getImageKey(), imageUrls(d), requested, d.getDescription(),
                d.getBestDate() == null ? null : d.getBestDate().atTime(d.getBestTime() == null ? java.time.LocalTime.NOON : d.getBestTime()),
                d.getHandover(), full ? d.getPhone() : null, d.getReviewNote());
    }

    static FoodRequestDto request(FoodRequest r) {
        Donation d = r.getDonation();
        return new FoodRequestDto(r.getId(), d.getId(), d.getFoodType(), qty(d.getQuantity(), d.getUnit()),
                d.getMeals(), d.getDonor().getName(), d.getDistanceKm(), pickupAt(d), d.getImageKey(),
                imageUrls(d), r.getNote(), r.getStatus().label());
    }
}
