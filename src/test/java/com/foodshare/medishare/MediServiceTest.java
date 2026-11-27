package com.foodshare.medishare;

import com.foodshare.medishare.MediService.MedicineForm;
import com.foodshare.medishare.MediService.StatusBody;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.service.NotificationService;
import com.foodshare.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Safety rules of MediShare: declarations, blocked terms, shelf life, sealed packs, quantity accounting, ownership. */
class MediServiceTest {
    private MedicineDonationRepository donations;
    private MedicineRequestRepository requests;
    private MediAuditRepository audit;
    private MediService service;
    private User hospital;
    private User ngo;

    @BeforeEach
    void setUp() {
        donations = mock(MedicineDonationRepository.class);
        requests = mock(MedicineRequestRepository.class);
        MediPolicyRepository policies = mock(MediPolicyRepository.class);
        audit = mock(MediAuditRepository.class);
        when(policies.findById(1L)).thenReturn(Optional.of(new MediPolicy()));
        when(donations.save(any(MedicineDonation.class))).thenAnswer(i -> i.getArgument(0));
        when(requests.save(any(MedicineRequest.class))).thenAnswer(i -> i.getArgument(0));
        service = new MediService(donations, requests, policies, audit, mock(UserRepository.class), mock(NotificationService.class));
        hospital = user(10L, Role.HOSPITAL);
        ngo = user(20L, Role.NGO);
    }

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setRole(role);
        u.setVerified(true);
        return u;
    }

    private MedicineForm form(String name, int shelfDays, boolean sealed, String storage, boolean a, boolean b, boolean c) {
        return new MedicineForm("Tablets/Capsules", name, "500 mg", "Tablet", "B-1", LocalDate.now().plusDays(shelfDays), 100, "strips",
                sealed, storage, "LIC-123", null, "Pharmacy block", "Delhi", "9876543210", a, b, c);
    }

    private MedicineForm good() {
        return form("Paracetamol", 400, true, "Room temperature", true, true, true);
    }

    private String rejectionOf(MedicineForm f) {
        return assertThrows(ApiException.class, () -> service.create(hospital, f)).getMessage();
    }

    @Test
    void validListingWaitsForAdminReviewByDefaultAndIsAudited() {
        var result = service.create(hospital, good());
        assertEquals("PENDING_REVIEW", result.status());
        assertEquals(100, result.quantityLeft());
        verify(audit, atLeastOnce()).save(any(MediAudit.class));
    }

    @Test
    void allThreeDeclarationsAreRequired() {
        assertTrue(rejectionOf(form("Paracetamol", 400, true, "Room temperature", false, true, true)).contains("authorised"));
        assertTrue(rejectionOf(form("Paracetamol", 400, true, "Room temperature", true, false, true)).contains("controlled"));
        assertTrue(rejectionOf(form("Paracetamol", 400, true, "Room temperature", true, true, false)).contains("unexpired"));
        verify(donations, never()).save(any(MedicineDonation.class));
    }

    @Test
    void restrictedTermsAreBlockedCaseInsensitively() {
        assertTrue(rejectionOf(form("TRAMADOL tablets", 400, true, "Room temperature", true, true, true)).contains("restricted"));
        assertTrue(rejectionOf(form("Insulin pens", 400, true, "Room temperature", true, true, true)).contains("restricted"));
        verify(donations, never()).save(any(MedicineDonation.class));
    }

    @Test
    void shelfLifeSealedPackAndColdChainRulesAreEnforced() {
        assertTrue(rejectionOf(form("Paracetamol", 30, true, "Room temperature", true, true, true)).contains("shelf life"));
        assertTrue(rejectionOf(form("Paracetamol", 400, false, "Room temperature", true, true, true)).contains("sealed"));
        assertTrue(rejectionOf(form("Paracetamol", 400, true, "Refrigerated (cold chain)", true, true, true)).contains("Cold-chain"));
    }

    private MedicineDonation listed(int qty) {
        MedicineDonation d = new MedicineDonation();
        d.setId(5L);
        d.setDonor(hospital);
        d.setMedicineName("Paracetamol");
        d.setUnit("strips");
        d.setExpiryDate(LocalDate.now().plusDays(400));
        d.setQuantity(qty);
        d.setQuantityLeft(qty);
        d.setStatus(MediStatus.LISTED);
        return d;
    }

    private MedicineRequest pending(MedicineDonation d, int qty) {
        MedicineRequest r = new MedicineRequest();
        r.setId(7L);
        r.setDonation(d);
        r.setRequester(ngo);
        r.setQuantity(qty);
        r.setStatus(MediRequestStatus.PENDING);
        when(requests.findById(7L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void approvingReservesUnitsAndRejectingAnApprovedRequestGivesThemBack() {
        MedicineDonation d = listed(100);
        MedicineRequest r = pending(d, 40);
        service.updateStatus(hospital, 7L, new StatusBody("APPROVED", null));
        assertEquals(60, d.getQuantityLeft());
        assertEquals(MediRequestStatus.APPROVED, r.getStatus());
        service.updateStatus(hospital, 7L, new StatusBody("REJECTED", "cancelled"));
        assertEquals(100, d.getQuantityLeft());
    }

    @Test
    void approvingEverythingMarksTheListingFullyAllocated() {
        MedicineDonation d = listed(10);
        pending(d, 10);
        service.updateStatus(hospital, 7L, new StatusBody("APPROVED", null));
        assertEquals(MediStatus.FULLY_ALLOCATED, d.getStatus());
    }

    @Test
    void cannotApproveMoreThanIsLeftOrAsSomeoneElse() {
        MedicineDonation d = listed(10);
        pending(d, 20);
        assertThrows(ApiException.class, () -> service.updateStatus(hospital, 7L, new StatusBody("APPROVED", null)));
        assertEquals(10, d.getQuantityLeft());
        ApiException e = assertThrows(ApiException.class, () -> service.updateStatus(user(99L, Role.HOSPITAL), 7L, new StatusBody("APPROVED", null)));
        assertEquals(403, e.getStatus().value());
    }

    @Test
    void requesterMayOnlyCancel() {
        MedicineDonation d = listed(10);
        pending(d, 5);
        assertThrows(ApiException.class, () -> service.updateStatus(ngo, 7L, new StatusBody("APPROVED", null)));
        assertDoesNotThrow(() -> service.updateStatus(ngo, 7L, new StatusBody("CANCELLED", null)));
    }
}
