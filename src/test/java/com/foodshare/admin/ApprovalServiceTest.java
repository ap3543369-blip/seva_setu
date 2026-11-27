package com.foodshare.admin;

import com.foodshare.admin.ApprovalService.ReviewBody;
import com.foodshare.bookshare.BookListingRepository;
import com.foodshare.common.ApprovalStatus;
import com.foodshare.medishare.MedicineDonationRepository;
import com.foodshare.model.Donation;
import com.foodshare.model.DonationStatus;
import com.foodshare.model.User;
import com.foodshare.ngo.NgoProfileRepository;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {
    private DonationRepository donations;
    private UserRepository users;
    private ApprovalService service;
    private User donor;
    private Donation d;

    @BeforeEach
    void setUp() {
        donations = mock(DonationRepository.class);
        users = mock(UserRepository.class);
        service = new ApprovalService(donations, mock(BookListingRepository.class), mock(MedicineDonationRepository.class),
                mock(NgoProfileRepository.class), users, mock(NotificationService.class));
        donor = new User();
        donor.setId(3L);
        donor.setName("Spice Kitchen");
        donor.setPoints(100);
        d = new Donation();
        d.setId(8L);
        d.setDonor(donor);
        d.setFoodType("Cooked Food");
        d.setPoints(150);
        d.setStatus(DonationStatus.PENDING);
        d.setApproval(ApprovalStatus.PENDING);
        d.setUnit("kg");
        d.setQuantity(20);
        d.setCity("Delhi");
        d.setPincode("110005");
        d.setAddress("Main road");
        when(donations.findById(8L)).thenReturn(Optional.of(d));
        when(donations.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById(3L)).thenReturn(Optional.of(donor));
    }

    @Test
    void approvingMakesTheDonationLiveAndGivesTheDonorHisPoints() {
        service.reviewFood(8L, new ReviewBody("approve", null));
        assertTrue(d.isApproved());
        assertEquals(DonationStatus.PENDING, d.getStatus()); // now visible to NGOs
        assertEquals(250, donor.getPoints());
        verify(users).save(donor);
    }

    @Test
    void rejectingNeedsAReasonClosesTheDonationAndGivesNoPoints() {
        assertThrows(ApiException.class, () -> service.reviewFood(8L, new ReviewBody("reject", "")));
        service.reviewFood(8L, new ReviewBody("reject", "Photo does not match the food"));
        assertFalse(d.isApproved());
        assertEquals(ApprovalStatus.REJECTED, d.getApproval());
        assertEquals(DonationStatus.CANCELLED, d.getStatus());
        assertEquals(100, donor.getPoints());
    }

    @Test
    void aDonationCanOnlyBeReviewedOnceAndUnknownDecisionsFail() {
        service.reviewFood(8L, new ReviewBody("approve", null));
        assertEquals(409, assertThrows(ApiException.class, () -> service.reviewFood(8L, new ReviewBody("approve", null))).getStatus().value());
        d.setApproval(ApprovalStatus.PENDING);
        assertThrows(ApiException.class, () -> service.reviewFood(8L, new ReviewBody("maybe", null)));
    }

    @Test
    void legacyDonationsWithoutApprovalCountAsApproved() {
        d.setApproval(null);
        assertTrue(d.isApproved());
        assertEquals(409, assertThrows(ApiException.class, () -> service.reviewFood(8L, new ReviewBody("approve", null))).getStatus().value());
    }
}
