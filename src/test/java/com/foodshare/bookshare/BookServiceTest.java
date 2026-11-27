package com.foodshare.bookshare;

import com.foodshare.common.ApprovalStatus;
import com.foodshare.common.ModuleAccessService;
import com.foodshare.common.PlatformModule;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.FileStorageService;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BookServiceTest {
    private BookListingRepository listings;
    private BookRequestRepository requests;
    private UserRepository users;
    private BookService service;
    private ModuleAccessService modules;
    private NotificationService notifications;
    private User donor;
    private User student;
    private BookListing book;
    private BookRequest req;

    @BeforeEach
    void setUp() {
        listings = mock(BookListingRepository.class);
        requests = mock(BookRequestRepository.class);
        users = mock(UserRepository.class);
        modules = mock(ModuleAccessService.class);
        notifications = mock(NotificationService.class);
        service = new BookService(listings, requests, users, notifications, mock(FileStorageService.class), modules);
        donor = user(1L, Role.DONOR);
        student = user(2L, Role.RECIPIENT);
        when(listings.save(any(BookListing.class))).thenAnswer(i -> i.getArgument(0));
        when(requests.save(any(BookRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById(1L)).thenReturn(Optional.of(donor));

        book = new BookListing();
        book.setId(5L);
        book.setDonor(donor);
        book.setTitle("NCERT Maths");
        book.setQuantity(5);
        book.setQuantityLeft(5);
        when(listings.findById(5L)).thenReturn(Optional.of(book));

        req = new BookRequest();
        req.setId(9L);
        req.setListing(book);
        req.setRequester(student);
        req.setQuantity(3);
        when(requests.findById(9L)).thenReturn(Optional.of(req));
    }

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setRole(role);
        return u;
    }

    @Test
    void acceptingReservesCopiesAndCompletingRewardsTheDonor() {
        service.updateStatus(donor, 9L, "accepted");
        assertEquals(2, book.getQuantityLeft());
        assertEquals(BookStatus.AVAILABLE, book.getStatus()); // 2 copies are still free

        when(requests.countByListingAndStatus(book, BookRequestStatus.ACCEPTED)).thenReturn(1L);
        service.updateStatus(donor, 9L, "COMPLETED");
        assertEquals(BookRequestStatus.COMPLETED, req.getStatus());
        assertEquals(30, donor.getPoints()); // 10 points per copy
        verify(users).save(donor);
    }

    @Test
    void cannotAcceptMoreCopiesThanAreLeft() {
        req.setQuantity(9);
        assertThrows(ApiException.class, () -> service.updateStatus(donor, 9L, "ACCEPTED"));
        assertEquals(5, book.getQuantityLeft());
    }

    @Test
    void requesterCancelRestoresAcceptedCopies() {
        service.updateStatus(donor, 9L, "ACCEPTED");
        assertEquals(2, book.getQuantityLeft());
        service.updateStatus(student, 9L, "CANCELLED");
        assertEquals(5, book.getQuantityLeft());
        assertEquals(BookRequestStatus.CANCELLED, req.getStatus());
    }

    @Test
    void strangersAndWrongTransitionsAreRejected() {
        ApiException e = assertThrows(ApiException.class, () -> service.updateStatus(user(77L, Role.NGO), 9L, "ACCEPTED"));
        assertEquals(403, e.getStatus().value());
        assertThrows(ApiException.class, () -> service.updateStatus(student, 9L, "ACCEPTED")); // requester cannot accept
        assertThrows(ApiException.class, () -> service.updateStatus(donor, 9L, "COMPLETED")); // must be accepted first
        assertThrows(ApiException.class, () -> service.updateStatus(donor, 9L, "NONSENSE"));
    }

    private BookService.BookForm form() {
        return new BookService.BookForm("Oxford Grammar", "Oxford", "English", "Class 9", "English", "Good", 2, "desc",
                "Delhi", "110005", "12 Street", "9876543210");
    }

    @Test
    void newListingWaitsForAdminApprovalWhenTheModuleRequiresIt() {
        when(modules.requiresApproval(PlatformModule.BOOKSHARE)).thenReturn(true);
        User admin = user(50L, Role.ADMIN);
        when(users.findByRoleOrderByNameAsc(Role.ADMIN)).thenReturn(List.of(admin));
        var dto = service.create(donor, form(), null);
        assertEquals("Awaiting approval", dto.status());
        verify(notifications).notify(eq(admin), contains("waiting for your approval"));
    }

    @Test
    void listingIsLiveImmediatelyWhenApprovalIsSwitchedOff() {
        when(modules.requiresApproval(PlatformModule.BOOKSHARE)).thenReturn(false);
        assertEquals("Available", service.create(donor, form(), null).status());
    }

    @Test
    void unapprovedListingIsHiddenAndCannotBeRequestedOrOpenedByStrangers() {
        book.setApproval(ApprovalStatus.PENDING);
        when(listings.findByStatusOrderByCreatedAtDesc(BookStatus.AVAILABLE)).thenReturn(List.of(book));
        assertTrue(service.available(student, null, null, null, null).isEmpty());
        assertThrows(ApiException.class, () -> service.createRequest(student, new BookService.CreateRequest(5L, 1, null)));
        ApiException e = assertThrows(ApiException.class, () -> service.detail(student, 5L));
        assertEquals(404, e.getStatus().value());
        assertEquals("Awaiting approval", service.detail(donor, 5L).status()); // the donor can see it
        assertEquals("Awaiting approval", service.detail(user(50L, Role.ADMIN), 5L).status()); // and so can an admin
    }
}
