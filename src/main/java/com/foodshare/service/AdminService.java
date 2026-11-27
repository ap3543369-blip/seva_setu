package com.foodshare.service;

import com.foodshare.dto.Dtos.AdminStats;
import com.foodshare.dto.Dtos.AdminUserDto;
import com.foodshare.dto.Dtos.DonationDto;
import com.foodshare.dto.Dtos.LeaderboardEntry;
import com.foodshare.model.DonationStatus;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.UserRepository;
import com.foodshare.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository users;
    private final DonationRepository donations;
    private final NotificationService notifications;
    private final com.foodshare.repo.ContactMessageRepository contacts;

    public AdminService(UserRepository users, DonationRepository donations, NotificationService notifications,
                        com.foodshare.repo.ContactMessageRepository contacts) {
        this.contacts = contacts;
        this.users = users;
        this.donations = donations;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public AdminStats stats() {
        return new AdminStats(users.countByRole(Role.DONOR), users.countByRole(Role.NGO),
                users.countByRoleAndVerifiedFalse(Role.NGO), donations.count(),
                donations.countByStatus(DonationStatus.DELIVERED), donations.sumAllMeals());
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> users() {
        return users.findAllByOrderByCreatedAtDesc().stream().filter(u -> u.getRole() != Role.ADMIN)
                .map(AdminService::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<DonationDto> donations() {
        return donations.findAllByOrderByCreatedAtDesc().stream().map(Mapper::donationFull).toList();
    }

    @Transactional
    public AdminUserDto toggleBlock(Long id) {
        User u = target(id);
        u.setBlocked(!u.isBlocked());
        return dto(users.save(u));
    }

    @Transactional
    public AdminUserDto verify(Long id) {
        User u = target(id);
        if (!com.foodshare.common.ModuleAccessService.needsVerification(u.getRole())) {
            throw ApiException.badRequest("Only NGO, hospital and recycler accounts need verification.");
        }
        u.setVerified(true);
        notifications.notify(u, u.getRole() == Role.NGO
                ? "Your NGO has been verified. You can now request donations."
                : "Your account has been verified by the SevaSetu admin.");
        return dto(users.save(u));
    }

    @Transactional(readOnly = true)
    public List<com.foodshare.dto.Dtos.ContactMessageDto> messages() {
        return contacts.findAllByOrderByCreatedAtDesc().stream().map(AdminService::msg).toList();
    }

    @Transactional
    public com.foodshare.dto.Dtos.ContactMessageDto toggleHandled(Long id) {
        com.foodshare.model.ContactMessage m = contacts.findById(id).orElseThrow(() -> ApiException.notFound("Message not found."));
        m.setHandled(!m.isHandled());
        return msg(contacts.save(m));
    }

    private static com.foodshare.dto.Dtos.ContactMessageDto msg(com.foodshare.model.ContactMessage m) {
        return new com.foodshare.dto.Dtos.ContactMessageDto(m.getId(), m.getName(), m.getEmail(), m.getSubject(),
                m.getMessage(), m.isHandled(), m.getCreatedAt());
    }

    /** Top donors by meals donated (cancelled donations do not count). */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard() {
        return users.findByRoleOrderByNameAsc(Role.DONOR).stream()
                .map(u -> new LeaderboardEntry(u.getName(), u.getLegacyMeals() + donations.sumMealsByDonor(u),
                        u.getLegacyDonations() + donations.countByDonorAndStatusNot(u, DonationStatus.CANCELLED)))
                .filter(e -> e.donations() > 0)
                .sorted(Comparator.comparingLong(LeaderboardEntry::meals).reversed())
                .limit(5).toList();
    }

    private User target(Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
        if (u.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("Admin accounts cannot be changed here.");
        }
        return u;
    }

    private static AdminUserDto dto(User u) {
        return new AdminUserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().key(), u.getOrgType(),
                u.isBlocked(), u.isVerified(), u.getCreatedAt());
    }
}
