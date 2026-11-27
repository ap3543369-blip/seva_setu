package com.foodshare.common;

import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.repo.UserRepository;
import com.foodshare.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Role-based + module-specific permissions.
 * A user can use a module when: the module is switched on, the role is allowed (or the admin granted it),
 * the admin did not revoke it, and any verification the module needs has been done.
 * Admins always pass. New modules / roles only need a change in DEFAULTS and verificationRequired().
 */
@Service
public class ModuleAccessService {

    public enum Status { AVAILABLE, COMING_SOON, NOT_FOR_ROLE, REVOKED, NEEDS_VERIFICATION }

    public record ModuleDto(String key, String label, String description, String icon, String status) {}
    public record PublicModuleDto(String key, String label, String description, String icon, boolean enabled) {}
    public record RoleInfo(String role, String label, String description, boolean available) {}
    public record AdminModuleDto(String key, String label, boolean enabled, boolean requireApproval, boolean approvalApplies) {}
    public record UserModuleDto(String key, String label, String status, String override) {}

    /** Which modules each role may use by default. */
    private static final Map<Role, Set<PlatformModule>> DEFAULTS = new EnumMap<>(Role.class);

    static {
        DEFAULTS.put(Role.DONOR, EnumSet.of(PlatformModule.FOODSHARE, PlatformModule.BOOKSHARE, PlatformModule.ECOAI));
        DEFAULTS.put(Role.RECIPIENT, EnumSet.of(PlatformModule.BOOKSHARE));
        DEFAULTS.put(Role.NGO, EnumSet.of(PlatformModule.FOODSHARE, PlatformModule.BOOKSHARE, PlatformModule.MEDISHARE));
        DEFAULTS.put(Role.HOSPITAL, EnumSet.of(PlatformModule.MEDISHARE));
        DEFAULTS.put(Role.RECYCLER, EnumSet.of(PlatformModule.ECOAI));
        DEFAULTS.put(Role.ADMIN, EnumSet.allOf(PlatformModule.class));
    }

    private static final Map<Role, String[]> ROLE_TEXT = new LinkedHashMap<>();

    static {
        ROLE_TEXT.put(Role.DONOR, new String[]{"Donor", "Restaurants, hotels, households and individuals who donate food, books or e-waste."});
        ROLE_TEXT.put(Role.RECIPIENT, new String[]{"Recipient", "Students and people who request books and study material."});
        ROLE_TEXT.put(Role.NGO, new String[]{"NGO", "Verified organisations that collect and distribute donations."});
        ROLE_TEXT.put(Role.HOSPITAL, new String[]{"Hospital / Pharmacy", "Authorised healthcare organisations taking part in medicine and supply donation."});
        ROLE_TEXT.put(Role.RECYCLER, new String[]{"Recycler", "Authorised recyclers who manage e-waste collection and recycling."});
    }

    private final ModuleSettingRepository settings;
    private final UserModuleAccessRepository overrides;
    private final UserRepository users;

    public ModuleAccessService(ModuleSettingRepository settings, UserModuleAccessRepository overrides, UserRepository users) {
        this.settings = settings;
        this.overrides = overrides;
        this.users = users;
    }

    // ---------------------------------------------------------------- rules

    /** Roles that must be verified by an admin before they are fully active. */
    public static boolean needsVerification(Role role) {
        return role == Role.NGO || role == Role.HOSPITAL || role == Role.RECYCLER;
    }

    /** Verification a module demands on top of the role default. FoodShare keeps its existing NGO behaviour. */
    private static boolean verificationRequired(Role role, PlatformModule module) {
        return role == Role.NGO // an NGO must be approved by the admin before it can open any module
                || (module == PlatformModule.MEDISHARE && role == Role.HOSPITAL)
                || (module == PlatformModule.ECOAI && role == Role.RECYCLER);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(PlatformModule m) {
        return settings.findById(m).map(ModuleSetting::isEnabled).orElse(m == PlatformModule.FOODSHARE);
    }

    @Transactional(readOnly = true)
    public Status statusFor(User u, PlatformModule m) {
        if (u.getRole() == Role.ADMIN) return Status.AVAILABLE;
        if (!isEnabled(m)) return Status.COMING_SOON;
        Optional<UserModuleAccess> o = overrides.findByUserAndModule(u, m);
        if (o.isPresent() && !o.get().isAllowed()) return Status.REVOKED;
        boolean granted = o.isPresent() && o.get().isAllowed();
        if (!granted && !DEFAULTS.getOrDefault(u.getRole(), Set.of()).contains(m)) return Status.NOT_FOR_ROLE;
        if (verificationRequired(u.getRole(), m) && !u.isVerified()) return Status.NEEDS_VERIFICATION;
        return Status.AVAILABLE;
    }

    /** Call at the top of a module's service/controller. Throws 403 with a clear reason. */
    public void require(User u, PlatformModule m) {
        Status s = statusFor(u, m);
        switch (s) {
            case AVAILABLE -> { }
            case COMING_SOON -> throw ApiException.forbidden(m.label() + " is not open yet.");
            case NOT_FOR_ROLE -> throw ApiException.forbidden("Your account type cannot use " + m.label() + ".");
            case REVOKED -> throw ApiException.forbidden("Your access to " + m.label() + " was turned off by an admin.");
            case NEEDS_VERIFICATION -> throw ApiException.forbidden("Your account must be verified by an admin before using " + m.label() + ".");
        }
    }

    // ---------------------------------------------------------------- views

    @Transactional(readOnly = true)
    public List<ModuleDto> modulesFor(User u) {
        return Arrays.stream(PlatformModule.values())
                .map(m -> new ModuleDto(m.key(), m.label(), m.description(), m.icon(), statusFor(u, m).name().toLowerCase()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicModuleDto> publicModules() {
        return Arrays.stream(PlatformModule.values())
                .map(m -> new PublicModuleDto(m.key(), m.label(), m.description(), m.icon(), isEnabled(m))).toList();
    }

    /** A role can be chosen at sign-up when at least one of its modules is switched on. */
    @Transactional(readOnly = true)
    public boolean roleAvailable(Role role) {
        return ROLE_TEXT.containsKey(role) && DEFAULTS.get(role).stream().anyMatch(this::isEnabled);
    }

    @Transactional(readOnly = true)
    public List<RoleInfo> roleInfos() {
        return ROLE_TEXT.entrySet().stream()
                .map(e -> new RoleInfo(e.getKey().key(), e.getValue()[0], e.getValue()[1], roleAvailable(e.getKey()))).toList();
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<AdminModuleDto> adminModules() {
        return Arrays.stream(PlatformModule.values()).map(m -> new AdminModuleDto(m.key(), m.label(), isEnabled(m), requiresApproval(m), approvalApplies(m))).toList();
    }

    @Transactional
    public AdminModuleDto toggle(PlatformModule m) {
        ModuleSetting s = settings.findById(m).orElseGet(() -> new ModuleSetting(m, isEnabled(m)));
        s.setEnabled(!s.isEnabled());
        settings.save(s);
        return new AdminModuleDto(m.key(), m.label(), s.isEnabled(), requiresApproval(m), approvalApplies(m));
    }

    /** Modules where a donor's new submission waits for admin approval (MediShare has its own review policy). */
    public static boolean approvalApplies(PlatformModule m) {
        return m == PlatformModule.FOODSHARE || m == PlatformModule.BOOKSHARE;
    }

    /** Must the admin approve new donations of this module before they go live? Default: yes. */
    @Transactional(readOnly = true)
    public boolean requiresApproval(PlatformModule m) {
        if (!approvalApplies(m)) return false;
        return settings.findById(m).map(ModuleSetting::getRequireApproval).orElse(true);
    }

    @Transactional
    public AdminModuleDto toggleApproval(PlatformModule m) {
        if (!approvalApplies(m)) throw ApiException.badRequest("Approval does not apply to this module.");
        ModuleSetting s = settings.findById(m).orElseGet(() -> new ModuleSetting(m, isEnabled(m)));
        s.setRequireApproval(!requiresApproval(m));
        settings.save(s);
        return new AdminModuleDto(m.key(), m.label(), s.isEnabled(), requiresApproval(m), approvalApplies(m));
    }

    @Transactional(readOnly = true)
    public List<UserModuleDto> userModules(Long userId) {
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        return Arrays.stream(PlatformModule.values()).map(m -> {
            Optional<UserModuleAccess> o = overrides.findByUserAndModule(target, m);
            String ov = o.map(x -> x.isAllowed() ? "granted" : "revoked").orElse(null);
            return new UserModuleDto(m.key(), m.label(), statusFor(target, m).name().toLowerCase(), ov);
        }).toList();
    }

    /** allowed = true grants, false revokes, null removes the override (back to the role default). */
    @Transactional
    public List<UserModuleDto> setOverride(Long userId, PlatformModule m, Boolean allowed) {
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        if (target.getRole() == Role.ADMIN) throw ApiException.badRequest("Admins always have access to every module.");
        Optional<UserModuleAccess> existing = overrides.findByUserAndModule(target, m);
        if (allowed == null) {
            existing.ifPresent(overrides::delete);
        } else {
            UserModuleAccess o = existing.orElseGet(UserModuleAccess::new);
            o.setUser(target);
            o.setModule(m);
            o.setAllowed(allowed);
            overrides.save(o);
        }
        return userModules(userId);
    }
}
