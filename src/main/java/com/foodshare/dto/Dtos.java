package com.foodshare.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Request / response payloads of the REST API. */
public final class Dtos {

    private Dtos() {
    }

    // ------------------------------------------------------------------ auth & profile

    public record SignupRequest(
            @NotBlank @Size(min = 2, max = 80, message = "Please enter your name.") String name,
            @NotBlank @Email(message = "Enter a valid email address.") String email,
            @NotBlank @Size(min = 6, max = 100, message = "Password must be at least 6 characters.") String password,
            @NotBlank @Pattern(regexp = "(?i)donor|ngo|recipient|hospital|recycler", message = "Choose a valid account type.") String role) {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter a valid email address.") String email,
            @NotBlank(message = "Password is required.") String password) {
    }

    public record ProfileRequest(
            @NotBlank @Size(min = 2, max = 80, message = "Name is required.") String name,
            @NotBlank @Size(max = 60) String orgType,
            @NotBlank @Email(message = "Enter a valid email.") String email,
            @NotBlank @Pattern(regexp = "^(\\+91[\\s-]?)?[6-9]\\d{9}$",
                    message = "Enter a valid Indian mobile number.") String phone) {
    }

    public record UserDto(Long id, String name, String email, String role, String orgType, String phone,
                          int points, boolean verified) {
    }

    public record AdminUserDto(Long id, String name, String email, String role, String orgType, boolean blocked,
                               boolean verified, LocalDateTime createdAt) {
    }

    public record AdminStats(long donors, long ngos, long pendingNgos, long donations, long delivered, long meals) {
    }

    public record NotificationDto(Long id, String message, boolean seen, LocalDateTime createdAt) {
    }

    public record NotificationsResponse(long unread, List<NotificationDto> items) {
    }

    public record LeaderboardEntry(String name, long meals, long donations) {
    }

    // ------------------------------------------------------------------ donations

    public record DonationRequest(
            @NotBlank(message = "Please choose a food type.") String foodType,
            @Positive(message = "Enter a quantity greater than 0.") double quantity,
            @NotBlank String unit,
            @Size(max = 1000) String description,
            @NotBlank String condition,
            @NotNull(message = "Choose a best-before date.") LocalDate bestDate,
            @NotNull LocalTime bestTime,
            @NotBlank(message = "Pickup address is required.") String address,
            @NotBlank(message = "City is required.") String city,
            @Pattern(regexp = "\\d{6}", message = "Enter a 6-digit PIN code.") String pincode,
            @Pattern(regexp = "[6-9]\\d{9}", message = "Enter a valid 10-digit mobile number.") String phone,
            String handover,
            @NotNull(message = "Choose a pickup date.") LocalDate pickupDate,
            @NotNull LocalTime timeFrom,
            @NotNull LocalTime timeTo,
            @Size(max = 1000) String notes) {
    }

    public record DonationDto(
            Long id,
            String food,
            String category,
            String qty,
            double quantity,
            String unit,
            int meals,
            int points,
            String status,
            String ngo,
            String donor,
            String area,
            String address,
            double distanceKm,
            LocalDateTime pickupAt,
            String condition,
            LocalDateTime createdAt,
            String imageKey,
            List<String> images,
            boolean requested,
            String description,
            LocalDateTime bestBefore,
            String handover,
            String phone,
            String reviewNote) {
    }

    public record NgoDto(Long id, String name, String orgType, long fulfilled, long meals, long partners) {
    }

    public record ContactRequest(
            @NotBlank @Size(max = 80, message = "Please enter your name.") String name,
            @NotBlank @Email(message = "Enter a valid email address.") String email,
            @NotBlank @Size(max = 120, message = "Please add a subject.") String subject,
            @NotBlank @Size(min = 10, max = 2000, message = "Message must be 10 to 2000 characters.") String message) {
    }

    public record ContactMessageDto(Long id, String name, String email, String subject, String message,
                                    boolean handled, LocalDateTime createdAt) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Enter your current password.") String currentPassword,
            @NotBlank @Size(min = 6, max = 100, message = "New password must be at least 6 characters.") String newPassword) {
    }

    public record StoryDto(Long id, String name, String role, String quote, String avatar) {
    }

    // ------------------------------------------------------------------ requests

    public record CreateFoodRequest(@NotNull Long donationId, @Size(max = 500) String note) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record FoodRequestDto(
            Long id,
            Long donationId,
            String food,
            String qty,
            int meals,
            String donor,
            double distanceKm,
            LocalDateTime pickupAt,
            String imageKey,
            List<String> images,
            String note,
            String status) {
    }

    // ------------------------------------------------------------------ dashboards

    public record DonorDashboard(String name, long donations, long meals, long co2, long gifts, int points,
                                 int milestone, List<DonationDto> recent) {
    }

    public record NgoDashboard(String name, long requests, long fulfilled, long meals, long partners) {
    }

    public record NgoChart(String month, List<Integer> values) {
    }

    // ------------------------------------------------------------------ rewards

    public record RewardOfferDto(String id, String brand, String logo, int amount, int cost) {
    }

    public record RewardsResponse(int points, List<RewardOfferDto> offers) {
    }

    public record RedeemedRewardDto(Long id, String brand, String logo, int amount, int cost, String code,
                                    LocalDateTime redeemedAt) {
    }

    public record RedeemResponse(RedeemedRewardDto reward, int pointsLeft) {
    }

    // ------------------------------------------------------------------ impact

    public record ImpactDto(long meals, long people, long co2, long donors, long ngos, List<String> labels,
                            List<Long> series, String caption) {
    }
}
