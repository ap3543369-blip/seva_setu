package com.foodshare.common;

import com.foodshare.common.ModuleAccessService.Status;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleAccessServiceTest {
    private ModuleSettingRepository settings;
    private UserModuleAccessRepository overrides;
    private ModuleAccessService service;

    @BeforeEach
    void setUp() {
        settings = mock(ModuleSettingRepository.class);
        overrides = mock(UserModuleAccessRepository.class);
        service = new ModuleAccessService(settings, overrides, mock(UserRepository.class));
        for (PlatformModule m : PlatformModule.values()) enable(m, true);
    }

    private void enable(PlatformModule m, boolean on) {
        when(settings.findById(m)).thenReturn(Optional.of(new ModuleSetting(m, on)));
    }

    private static User user(Role role, boolean verified) {
        User u = new User();
        u.setId(1L);
        u.setRole(role);
        u.setVerified(verified);
        return u;
    }

    @Test
    void roleDefaultsDecideAccess() {
        assertEquals(Status.AVAILABLE, service.statusFor(user(Role.DONOR, true), PlatformModule.FOODSHARE));
        assertEquals(Status.AVAILABLE, service.statusFor(user(Role.RECIPIENT, true), PlatformModule.BOOKSHARE));
        assertEquals(Status.NOT_FOR_ROLE, service.statusFor(user(Role.RECIPIENT, true), PlatformModule.FOODSHARE));
        assertEquals(Status.NOT_FOR_ROLE, service.statusFor(user(Role.DONOR, true), PlatformModule.MEDISHARE));
        assertEquals(Status.NOT_FOR_ROLE, service.statusFor(user(Role.RECYCLER, true), PlatformModule.BOOKSHARE));
    }

    @Test
    void switchedOffModuleIsComingSoonExceptForAdmins() {
        enable(PlatformModule.BOOKSHARE, false);
        assertEquals(Status.COMING_SOON, service.statusFor(user(Role.DONOR, true), PlatformModule.BOOKSHARE));
        assertEquals(Status.AVAILABLE, service.statusFor(user(Role.ADMIN, true), PlatformModule.BOOKSHARE));
    }

    @Test
    void ngosMustBeVerifiedForEveryModuleAndOtherRolesOnlyWhereNeeded() {
        // every module an NGO can use waits for admin verification; EcoAI is not an NGO module at all
        for (PlatformModule m : new PlatformModule[]{PlatformModule.FOODSHARE, PlatformModule.BOOKSHARE, PlatformModule.MEDISHARE}) {
            assertEquals(Status.NEEDS_VERIFICATION, service.statusFor(user(Role.NGO, false), m), m.name());
        }
        assertEquals(Status.NOT_FOR_ROLE, service.statusFor(user(Role.NGO, false), PlatformModule.ECOAI));
        assertEquals(Status.AVAILABLE, service.statusFor(user(Role.NGO, true), PlatformModule.FOODSHARE));
        assertEquals(Status.NEEDS_VERIFICATION, service.statusFor(user(Role.HOSPITAL, false), PlatformModule.MEDISHARE));
        assertEquals(Status.AVAILABLE, service.statusFor(user(Role.HOSPITAL, true), PlatformModule.MEDISHARE));
        assertEquals(Status.NEEDS_VERIFICATION, service.statusFor(user(Role.RECYCLER, false), PlatformModule.ECOAI));
        assertEquals(Status.AVAILABLE, service.statusFor(user(Role.DONOR, false), PlatformModule.FOODSHARE)); // donors never need verification
    }

    @Test
    void adminApprovalOfDonationsDefaultsToOnForFoodAndBooksOnly() {
        assertTrue(service.requiresApproval(PlatformModule.FOODSHARE));
        assertTrue(service.requiresApproval(PlatformModule.BOOKSHARE));
        assertFalse(service.requiresApproval(PlatformModule.MEDISHARE)); // has its own review policy
        assertFalse(service.requiresApproval(PlatformModule.ECOAI));
        ModuleSetting off = new ModuleSetting(PlatformModule.FOODSHARE, true);
        off.setRequireApproval(false);
        when(settings.findById(PlatformModule.FOODSHARE)).thenReturn(Optional.of(off));
        assertFalse(service.requiresApproval(PlatformModule.FOODSHARE));
        assertThrows(ApiException.class, () -> service.toggleApproval(PlatformModule.ECOAI));
    }

    @Test
    void adminOverridesGrantAndRevoke() {
        User donor = user(Role.DONOR, true);
        UserModuleAccess revoke = new UserModuleAccess();
        revoke.setAllowed(false);
        when(overrides.findByUserAndModule(donor, PlatformModule.FOODSHARE)).thenReturn(Optional.of(revoke));
        assertEquals(Status.REVOKED, service.statusFor(donor, PlatformModule.FOODSHARE));

        UserModuleAccess grant = new UserModuleAccess();
        grant.setAllowed(true);
        when(overrides.findByUserAndModule(donor, PlatformModule.MEDISHARE)).thenReturn(Optional.of(grant));
        assertEquals(Status.AVAILABLE, service.statusFor(donor, PlatformModule.MEDISHARE));
    }

    @Test
    void requireThrowsForbiddenWithReason() {
        ApiException e = assertThrows(ApiException.class, () -> service.require(user(Role.HOSPITAL, false), PlatformModule.MEDISHARE));
        assertEquals(403, e.getStatus().value());
        assertTrue(e.getMessage().contains("verified"));
        assertDoesNotThrow(() -> service.require(user(Role.DONOR, true), PlatformModule.BOOKSHARE));
    }

    @Test
    void signupRoleIsOnlyOfferedWhenOneOfItsModulesIsOn() {
        assertTrue(service.roleAvailable(Role.RECIPIENT));
        enable(PlatformModule.BOOKSHARE, false);
        assertFalse(service.roleAvailable(Role.RECIPIENT));
        assertTrue(service.roleAvailable(Role.DONOR)); // donors still have FoodShare / EcoAI
        assertFalse(service.roleAvailable(Role.ADMIN)); // never selectable at sign-up
    }

    @Test
    void needsVerificationRoles() {
        assertTrue(ModuleAccessService.needsVerification(Role.NGO));
        assertTrue(ModuleAccessService.needsVerification(Role.HOSPITAL));
        assertTrue(ModuleAccessService.needsVerification(Role.RECYCLER));
        assertFalse(ModuleAccessService.needsVerification(Role.DONOR));
        assertFalse(ModuleAccessService.needsVerification(Role.RECIPIENT));
    }
}
