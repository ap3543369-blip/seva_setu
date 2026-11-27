package com.foodshare.bookshare;

import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.FileStorageService;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BookService {

    // ---------------------------------------------------------------- request / response shapes
    public record BookForm(
            @NotBlank(message = "Enter the book title.") @Size(max = 150) String title,
            @Size(max = 120) String author,
            @NotBlank(message = "Choose a subject.") @Size(max = 80) String subject,
            @NotBlank(message = "Choose the class / level.") @Size(max = 60) String level,
            @Size(max = 40) String language,
            @NotBlank @Pattern(regexp = "New|Good|Fair", message = "Condition must be New, Good or Fair.") String condition,
            @Min(value = 1, message = "At least 1 copy.") @Max(value = 500, message = "At most 500 copies.") int quantity,
            @Size(max = 1000) String description,
            @NotBlank(message = "City is required.") String city,
            @Pattern(regexp = "\\d{6}", message = "Enter a 6-digit PIN code.") String pincode,
            @NotBlank(message = "Pickup address is required.") String address,
            @Pattern(regexp = "[6-9]\\d{9}", message = "Enter a valid 10-digit mobile number.") String phone) {
    }

    public record CreateRequest(@NotNull(message = "Choose a book.") Long listingId,
                                @Min(value = 1, message = "At least 1 copy.") int quantity,
                                @Size(max = 500) String note) {
    }

    public record StatusBody(@NotBlank String status) {
    }

    public record BookDto(Long id, String title, String author, String subject, String level, String language,
                          String condition, int quantity, int quantityLeft, String description, String city,
                          String donor, String address, String phone, List<String> images, String status,
                          LocalDateTime createdAt, Long pendingRequests, String myRequestStatus, String reviewNote) {
    }

    public record BookRequestDto(Long id, Long listingId, String title, String subject, int quantity, String note,
                                 String status, String counterpart, String counterpartType, String phone,
                                 String address, List<String> images, LocalDateTime createdAt) {
    }

    public record Stats(long listings, long copiesGiven, long pendingRequests, long myRequests, long myAccepted,
                        long copiesReceived) {
    }

    private final BookListingRepository listings;
    private final BookRequestRepository requests;
    private final UserRepository users;
    private final NotificationService notifications;
    private final FileStorageService files;
    private final com.foodshare.common.ModuleAccessService modules;

    public BookService(BookListingRepository listings, BookRequestRepository requests, UserRepository users,
                       NotificationService notifications, FileStorageService files,
                       com.foodshare.common.ModuleAccessService modules) {
        this.modules = modules;
        this.listings = listings;
        this.requests = requests;
        this.users = users;
        this.notifications = notifications;
        this.files = files;
    }

    // ---------------------------------------------------------------- donor side
    @Transactional
    public BookDto create(User donor, BookForm f, List<MultipartFile> images) {
        List<MultipartFile> photos = images == null ? List.of() : images.stream().filter(m -> !m.isEmpty()).toList();
        if (photos.size() > 4) throw ApiException.badRequest("You can upload at most 4 photos.");
        BookListing b = new BookListing();
        b.setDonor(donor);
        b.setTitle(f.title().trim());
        b.setAuthor(blankToNull(f.author()));
        b.setSubject(f.subject().trim());
        b.setLevel(f.level().trim());
        b.setLanguage(blankToNull(f.language()));
        b.setBookCondition(f.condition());
        b.setQuantity(f.quantity());
        b.setQuantityLeft(f.quantity());
        b.setDescription(blankToNull(f.description()));
        b.setCity(f.city().trim());
        b.setPincode(f.pincode());
        b.setAddress(f.address().trim());
        b.setPhone(f.phone());
        for (MultipartFile m : photos) b.getImageFiles().add(files.storeImage(m));
        boolean needsApproval = modules.requiresApproval(com.foodshare.common.PlatformModule.BOOKSHARE);
        b.setApproval(needsApproval ? com.foodshare.common.ApprovalStatus.PENDING : com.foodshare.common.ApprovalStatus.APPROVED);
        listings.save(b);
        if (needsApproval) { // the admin sees it in Approvals → Books
            for (User admin : users.findByRoleOrderByNameAsc(Role.ADMIN)) {
                notifications.notify(admin, "New BOOK listing from " + donor.getName() + " (\"" + b.getTitle() + "\" x" + b.getQuantity() + ") is waiting for your approval.");
            }
        }
        return dto(b, donor);
    }

    @Transactional(readOnly = true)
    public List<BookDto> mine(User donor) {
        return listings.findByDonorOrderByCreatedAtDesc(donor).stream().map(b -> dto(b, donor)).toList();
    }

    @Transactional
    public BookDto cancel(User donor, Long id) {
        BookListing b = find(id);
        if (!b.getDonor().getId().equals(donor.getId())) throw ApiException.forbidden("This is not your listing.");
        if (b.getStatus() == BookStatus.DONATED || b.getStatus() == BookStatus.CANCELLED) {
            throw ApiException.conflict("This listing can no longer be cancelled.");
        }
        for (BookRequest r : requests.findByListingOrderByCreatedAtDesc(b)) {
            if (r.getStatus() == BookRequestStatus.PENDING || r.getStatus() == BookRequestStatus.ACCEPTED) {
                r.setStatus(BookRequestStatus.DECLINED);
                requests.save(r);
                notifications.notify(r.getRequester(), "\"" + b.getTitle() + "\" was withdrawn by the donor.");
            }
        }
        b.setStatus(BookStatus.CANCELLED);
        b.setQuantityLeft(0);
        return dto(listings.save(b), donor);
    }

    @Transactional(readOnly = true)
    public List<BookRequestDto> forListing(User donor, Long listingId) {
        BookListing b = find(listingId);
        if (donor.getRole() != Role.ADMIN && !b.getDonor().getId().equals(donor.getId())) {
            throw ApiException.forbidden("This is not your listing.");
        }
        return requests.findByListingOrderByCreatedAtDesc(b).stream().map(r -> requestDto(r, donor)).toList();
    }

    // ---------------------------------------------------------------- browsing
    @Transactional(readOnly = true)
    public List<BookDto> available(User viewer, String q, String subject, String level, String city) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return listings.findByStatusOrderByCreatedAtDesc(BookStatus.AVAILABLE).stream()
                .filter(BookListing::isApproved)
                .filter(b -> needle.isEmpty() || (b.getTitle() + " " + nz(b.getAuthor()) + " " + b.getSubject()).toLowerCase(Locale.ROOT).contains(needle))
                .filter(b -> blank(subject) || b.getSubject().equalsIgnoreCase(subject.trim()))
                .filter(b -> blank(level) || b.getLevel().equalsIgnoreCase(level.trim()))
                .filter(b -> blank(city) || b.getCity().toLowerCase(Locale.ROOT).contains(city.trim().toLowerCase(Locale.ROOT)))
                .map(b -> dto(b, viewer)).toList();
    }

    @Transactional(readOnly = true)
    public BookDto detail(User viewer, Long id) {
        BookListing b = find(id);
        boolean owner = b.getDonor().getId().equals(viewer.getId());
        if (!b.isApproved() && !owner && viewer.getRole() != Role.ADMIN) throw ApiException.notFound("Book listing not found.");
        return dto(b, viewer);
    }

    // ---------------------------------------------------------------- requester side
    @Transactional
    public BookRequestDto createRequest(User requester, CreateRequest body) {
        BookListing b = find(body.listingId());
        if (b.getStatus() != BookStatus.AVAILABLE || !b.isApproved()) throw ApiException.conflict("These books are no longer available.");
        if (body.quantity() > b.getQuantityLeft()) {
            throw ApiException.badRequest("Only " + b.getQuantityLeft() + " cop" + (b.getQuantityLeft() == 1 ? "y is" : "ies are") + " left.");
        }
        if (requests.findByListingAndRequester(b, requester).isPresent()) {
            throw ApiException.conflict("You have already requested this book.");
        }
        BookRequest r = new BookRequest();
        r.setListing(b);
        r.setRequester(requester);
        r.setQuantity(body.quantity());
        r.setNote(blankToNull(body.note()));
        requests.save(r);
        notifications.notify(b.getDonor(), requester.getName() + " requested " + r.getQuantity() + " cop" + (r.getQuantity() == 1 ? "y" : "ies") + " of \"" + b.getTitle() + "\".");
        return requestDto(r, requester);
    }

    @Transactional(readOnly = true)
    public List<BookRequestDto> myRequests(User requester) {
        return requests.findByRequesterOrderByCreatedAtDesc(requester).stream().map(r -> requestDto(r, requester)).toList();
    }

    // ---------------------------------------------------------------- status changes
    @Transactional
    public BookRequestDto updateStatus(User actor, Long requestId, String statusText) {
        BookRequest r = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request not found."));
        BookListing b = r.getListing();
        BookRequestStatus next;
        try {
            next = BookRequestStatus.valueOf(statusText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown status.");
        }
        boolean isDonor = b.getDonor().getId().equals(actor.getId());
        boolean isRequester = r.getRequester().getId().equals(actor.getId());
        BookRequestStatus cur = r.getStatus();

        if (isDonor) {
            if (cur == BookRequestStatus.PENDING && next == BookRequestStatus.ACCEPTED) {
                if (b.getStatus() != BookStatus.AVAILABLE || r.getQuantity() > b.getQuantityLeft()) {
                    throw ApiException.conflict("Not enough copies left to accept this request.");
                }
                b.setQuantityLeft(b.getQuantityLeft() - r.getQuantity());
                notifications.notify(r.getRequester(), "Your request for \"" + b.getTitle() + "\" was accepted. Contact details are now visible.");
            } else if (cur == BookRequestStatus.PENDING && next == BookRequestStatus.DECLINED) {
                notifications.notify(r.getRequester(), "Your request for \"" + b.getTitle() + "\" was declined.");
            } else if (cur == BookRequestStatus.ACCEPTED && next == BookRequestStatus.DECLINED) {
                b.setQuantityLeft(b.getQuantityLeft() + r.getQuantity());
                notifications.notify(r.getRequester(), "The donor cancelled the handover of \"" + b.getTitle() + "\".");
            } else if (cur == BookRequestStatus.ACCEPTED && next == BookRequestStatus.COMPLETED) {
                User d = users.findById(b.getDonor().getId()).orElse(b.getDonor());
                d.setPoints(d.getPoints() + Math.min(200, 10 * r.getQuantity())); // reuses the FoodShare reward points
                users.save(d);
                notifications.notify(r.getRequester(), "Handover of \"" + b.getTitle() + "\" is marked complete. Happy reading!");
            } else {
                throw ApiException.badRequest("This status change is not allowed.");
            }
        } else if (isRequester) {
            if ((cur == BookRequestStatus.PENDING || cur == BookRequestStatus.ACCEPTED) && next == BookRequestStatus.CANCELLED) {
                if (cur == BookRequestStatus.ACCEPTED) b.setQuantityLeft(b.getQuantityLeft() + r.getQuantity());
                notifications.notify(b.getDonor(), r.getRequester().getName() + " cancelled the request for \"" + b.getTitle() + "\".");
            } else {
                throw ApiException.badRequest("You can only cancel a pending or accepted request.");
            }
        } else {
            throw ApiException.forbidden("This request is not yours.");
        }
        r.setStatus(next);
        requests.save(r);
        refreshStatus(b);
        return requestDto(r, actor);
    }

    @Transactional(readOnly = true)
    public Stats stats(User u) {
        List<BookListing> own = listings.findByDonorOrderByCreatedAtDesc(u);
        long given = 0, pending = 0;
        for (BookListing b : own) {
            for (BookRequest r : requests.findByListingOrderByCreatedAtDesc(b)) {
                if (r.getStatus() == BookRequestStatus.COMPLETED) given += r.getQuantity();
                if (r.getStatus() == BookRequestStatus.PENDING) pending++;
            }
        }
        List<BookRequest> mine = requests.findByRequesterOrderByCreatedAtDesc(u);
        long accepted = mine.stream().filter(r -> r.getStatus() == BookRequestStatus.ACCEPTED || r.getStatus() == BookRequestStatus.COMPLETED).count();
        long received = mine.stream().filter(r -> r.getStatus() == BookRequestStatus.COMPLETED).mapToLong(BookRequest::getQuantity).sum();
        return new Stats(own.size(), given, pending, mine.size(), accepted, received);
    }

    // ---------------------------------------------------------------- helpers
    private void refreshStatus(BookListing b) {
        if (b.getStatus() == BookStatus.CANCELLED) return;
        if (b.getQuantityLeft() > 0) {
            b.setStatus(BookStatus.AVAILABLE);
        } else {
            long open = requests.countByListingAndStatus(b, BookRequestStatus.ACCEPTED);
            b.setStatus(open > 0 ? BookStatus.RESERVED : BookStatus.DONATED);
        }
        listings.save(b);
    }

    private BookListing find(Long id) {
        return listings.findById(id).orElseThrow(() -> ApiException.notFound("Book listing not found."));
    }

    /** Address and phone are only shown to the donor, admins and a requester whose request was accepted. */
    private boolean canSeeContact(BookListing b, User viewer) {
        if (viewer == null) return false;
        if (viewer.getRole() == Role.ADMIN || b.getDonor().getId().equals(viewer.getId())) return true;
        return requests.findByListingAndRequester(b, viewer)
                .map(r -> r.getStatus() == BookRequestStatus.ACCEPTED || r.getStatus() == BookRequestStatus.COMPLETED).orElse(false);
    }

    private BookDto dto(BookListing b, User viewer) {
        boolean owner = viewer != null && b.getDonor().getId().equals(viewer.getId());
        boolean contact = canSeeContact(b, viewer);
        Long pending = owner ? requests.countByListingAndStatus(b, BookRequestStatus.PENDING) : null;
        String mine = viewer == null ? null : requests.findByListingAndRequester(b, viewer).map(r -> r.getStatus().label()).orElse(null);
        return new BookDto(b.getId(), b.getTitle(), b.getAuthor(), b.getSubject(), b.getLevel(), b.getLanguage(),
                b.getBookCondition(), b.getQuantity(), b.getQuantityLeft(), b.getDescription(), b.getCity(),
                b.getDonor().getName(), contact ? b.getAddress() : null, contact ? b.getPhone() : null,
                b.getImageFiles().stream().map(f -> "/uploads/" + f).toList(),
                b.isApproved() ? b.getStatus().label() : b.getApproval().label(),
                b.getCreatedAt(), pending, mine, b.getReviewNote());
    }

    private BookRequestDto requestDto(BookRequest r, User viewer) {
        BookListing b = r.getListing();
        boolean viewerIsDonor = b.getDonor().getId().equals(viewer.getId());
        boolean shared = r.getStatus() == BookRequestStatus.ACCEPTED || r.getStatus() == BookRequestStatus.COMPLETED;
        User other = viewerIsDonor ? r.getRequester() : b.getDonor();
        return new BookRequestDto(r.getId(), b.getId(), b.getTitle(), b.getSubject(), r.getQuantity(), r.getNote(),
                r.getStatus().label(), other.getName(), other.getOrgType(),
                shared ? (viewerIsDonor ? other.getPhone() : b.getPhone()) : null,
                shared && !viewerIsDonor ? b.getAddress() : null,
                b.getImageFiles().stream().map(f -> "/uploads/" + f).toList(), r.getCreatedAt());
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String blankToNull(String s) { return blank(s) ? null : s.trim(); }
}
