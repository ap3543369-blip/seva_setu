package com.foodshare.admin;

import com.foodshare.bookshare.BookListing;
import com.foodshare.bookshare.BookListingRepository;
import com.foodshare.bookshare.BookStatus;
import com.foodshare.common.ApprovalStatus;
import com.foodshare.medishare.MediStatus;
import com.foodshare.medishare.MedicineDonation;
import com.foodshare.medishare.MedicineDonationRepository;
import com.foodshare.model.Donation;
import com.foodshare.model.DonationStatus;
import com.foodshare.model.User;
import com.foodshare.ngo.NgoApplicationStatus;
import com.foodshare.ngo.NgoProfile;
import com.foodshare.ngo.NgoProfileRepository;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One place for the admin to see everything that waits for a decision, split by type:
 * NGO applications, food donations, book listings, medicine listings. (Approving medicine listings and NGO
 * applications has its own endpoints; this class only counts them for the overview.)
 */
@Service
public class ApprovalService {

    public record ReviewBody(@NotBlank String decision, @Size(max = 500) String note) {
    }

    public record FeedItem(String type, Long id, String title, String by, LocalDateTime at) {
    }

    public record Summary(long ngoApplications, long food, long books, long medicine, long total, List<FeedItem> recent) {
    }

    public record FoodReviewDto(Long id, String food, String category, String qty, int meals, int points, String donor,
                                String donorEmail, String donorType, String donorPhone, String city, String address,
                                String description, List<String> images, String approval, String approvalLabel,
                                String reviewNote, String status, LocalDateTime createdAt) {
    }

    public record BookReviewDto(Long id, String title, String author, String subject, String level, String condition,
                                int quantity, String donor, String donorEmail, String donorType, String donorPhone,
                                String city, String address, String description, List<String> images, String approval,
                                String approvalLabel, String reviewNote, String status, LocalDateTime createdAt) {
    }

    private final DonationRepository donations;
    private final BookListingRepository books;
    private final MedicineDonationRepository medicines;
    private final NgoProfileRepository ngoProfiles;
    private final UserRepository users;
    private final NotificationService notifications;

    public ApprovalService(DonationRepository donations, BookListingRepository books, MedicineDonationRepository medicines,
                           NgoProfileRepository ngoProfiles, UserRepository users, NotificationService notifications) {
        this.donations = donations;
        this.books = books;
        this.medicines = medicines;
        this.ngoProfiles = ngoProfiles;
        this.users = users;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- overview
    @Transactional(readOnly = true)
    public Summary summary() {
        List<FeedItem> feed = new ArrayList<>();
        long food = 0, bookCount = 0;
        for (Donation d : donations.findAllByOrderByCreatedAtDesc()) {
            if (d.getApproval() == ApprovalStatus.PENDING) {
                food++;
                feed.add(new FeedItem("food", d.getId(), d.getFoodType() + " · " + qty(d), d.getDonor().getName(), d.getCreatedAt()));
            }
        }
        for (BookListing b : books.findAllByOrderByCreatedAtDesc()) {
            if (b.getApproval() == ApprovalStatus.PENDING) {
                bookCount++;
                feed.add(new FeedItem("book", b.getId(), b.getTitle() + " x" + b.getQuantity(), b.getDonor().getName(), b.getCreatedAt()));
            }
        }
        List<MedicineDonation> meds = medicines.findByStatusOrderByCreatedAtDesc(MediStatus.PENDING_REVIEW);
        for (MedicineDonation m : meds) {
            feed.add(new FeedItem("medicine", m.getId(), m.getMedicineName() + " x" + m.getQuantity() + " " + m.getUnit(), m.getDonor().getName(), m.getCreatedAt()));
        }
        long ngo = 0;
        for (NgoProfile p : ngoProfiles.findAllByOrderBySubmittedAtDesc()) {
            if (p.getStatus() == NgoApplicationStatus.SUBMITTED) {
                ngo++;
                feed.add(new FeedItem("ngo", p.getId(), p.getUser().getName() + " · reg. " + p.getRegistrationNumber(), p.getContactPerson(), p.getSubmittedAt()));
            }
        }
        feed.sort(Comparator.comparing(FeedItem::at, Comparator.nullsLast(Comparator.reverseOrder())));
        return new Summary(ngo, food, bookCount, meds.size(), ngo + food + bookCount + meds.size(), feed.stream().limit(15).toList());
    }

    // ---------------------------------------------------------------- food
    @Transactional(readOnly = true)
    public List<FoodReviewDto> food(String status) {
        boolean all = "all".equalsIgnoreCase(status);
        return donations.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> all ? d.getApproval() != null : d.getApproval() == ApprovalStatus.PENDING)
                .map(ApprovalService::foodDto).toList();
    }

    @Transactional
    public FoodReviewDto reviewFood(Long id, ReviewBody b) {
        Donation d = donations.findById(id).orElseThrow(() -> ApiException.notFound("Donation not found."));
        if (d.getApproval() != ApprovalStatus.PENDING) throw ApiException.conflict("This donation was already reviewed.");
        String note = clean(b.note());
        switch (b.decision().trim().toLowerCase(Locale.ROOT)) {
            case "approve" -> {
                d.setApproval(ApprovalStatus.APPROVED);
                User donor = users.findById(d.getDonor().getId()).orElseThrow();
                donor.setPoints(donor.getPoints() + d.getPoints()); // points are given once the donation is approved
                users.save(donor);
                notifications.notify(d.getDonor(), "Your " + d.getFoodType() + " donation was approved and is now visible to NGOs.");
            }
            case "reject" -> {
                if (note == null) throw ApiException.badRequest("Please tell the donor why it was rejected.");
                d.setApproval(ApprovalStatus.REJECTED);
                d.setStatus(DonationStatus.CANCELLED);
                notifications.notify(d.getDonor(), "Your " + d.getFoodType() + " donation was rejected: " + note);
            }
            default -> throw ApiException.badRequest("Decision must be approve or reject.");
        }
        d.setReviewNote(note);
        return foodDto(donations.save(d));
    }

    // ---------------------------------------------------------------- books
    @Transactional(readOnly = true)
    public List<BookReviewDto> books(String status) {
        boolean all = "all".equalsIgnoreCase(status);
        return books.findAllByOrderByCreatedAtDesc().stream()
                .filter(b -> all ? b.getApproval() != null : b.getApproval() == ApprovalStatus.PENDING)
                .map(ApprovalService::bookDto).toList();
    }

    @Transactional
    public BookReviewDto reviewBook(Long id, ReviewBody body) {
        BookListing b = books.findById(id).orElseThrow(() -> ApiException.notFound("Book listing not found."));
        if (b.getApproval() != ApprovalStatus.PENDING) throw ApiException.conflict("This listing was already reviewed.");
        String note = clean(body.note());
        switch (body.decision().trim().toLowerCase(Locale.ROOT)) {
            case "approve" -> {
                b.setApproval(ApprovalStatus.APPROVED);
                notifications.notify(b.getDonor(), "Your book listing \"" + b.getTitle() + "\" was approved and is now visible.");
            }
            case "reject" -> {
                if (note == null) throw ApiException.badRequest("Please tell the donor why it was rejected.");
                b.setApproval(ApprovalStatus.REJECTED);
                b.setStatus(BookStatus.CANCELLED);
                b.setQuantityLeft(0);
                notifications.notify(b.getDonor(), "Your book listing \"" + b.getTitle() + "\" was rejected: " + note);
            }
            default -> throw ApiException.badRequest("Decision must be approve or reject.");
        }
        b.setReviewNote(note);
        return bookDto(books.save(b));
    }

    // ---------------------------------------------------------------- helpers
    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String qty(Donation d) {
        double q = d.getQuantity();
        return (q == Math.floor(q) ? String.valueOf((long) q) : String.valueOf(q)) + " " + d.getUnit();
    }

    private static FoodReviewDto foodDto(Donation d) {
        User u = d.getDonor();
        ApprovalStatus a = d.getApproval() == null ? ApprovalStatus.APPROVED : d.getApproval();
        return new FoodReviewDto(d.getId(), d.getFoodType(), d.getCategory(), qty(d), d.getMeals(), d.getPoints(), u.getName(),
                u.getEmail(), u.getOrgType(), u.getPhone(), d.getCity(), d.getAddress() + ", " + d.getCity() + " " + d.getPincode(),
                d.getDescription(), d.getImageFiles().stream().map(f -> "/uploads/" + f).toList(), a.name(), a.label(),
                d.getReviewNote(), d.getStatus().label(), d.getCreatedAt());
    }

    private static BookReviewDto bookDto(BookListing b) {
        User u = b.getDonor();
        ApprovalStatus a = b.getApproval() == null ? ApprovalStatus.APPROVED : b.getApproval();
        return new BookReviewDto(b.getId(), b.getTitle(), b.getAuthor(), b.getSubject(), b.getLevel(), b.getBookCondition(),
                b.getQuantity(), u.getName(), u.getEmail(), u.getOrgType(), u.getPhone(), b.getCity(),
                b.getAddress() + ", " + b.getCity() + " " + b.getPincode(), b.getDescription(),
                b.getImageFiles().stream().map(f -> "/uploads/" + f).toList(), a.name(), a.label(), b.getReviewNote(),
                b.getStatus().label(), b.getCreatedAt());
    }
}
