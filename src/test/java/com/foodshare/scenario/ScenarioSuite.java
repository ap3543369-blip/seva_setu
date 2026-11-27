package com.foodshare.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodshare.admin.ApprovalService;
import com.foodshare.admin.ReportController;
import com.foodshare.bookshare.*;
import com.foodshare.common.*;
import com.foodshare.dto.Dtos.*;
import com.foodshare.ecoai.*;
import com.foodshare.medishare.*;
import com.foodshare.model.*;
import com.foodshare.ngo.*;
import com.foodshare.repo.*;
import com.foodshare.service.*;
import com.foodshare.web.ApiException;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * End-to-end scenarios that run the REAL services (auth, approvals, NGO registration, BookShare, MediShare, EcoAI, reports, demo data)
 * against in-memory repositories, and talk to the real Python forecast service when Python + its libraries are available.
 * Run with `mvn test` (ScenarioSuiteTest) or `ScenarioSuite.main`.
 */
public class ScenarioSuite {
    static int passed = 0, failed = 0;
    static final List<String> failures = new ArrayList<>();
    static boolean pythonUp = false;

    public record Result(int passed, int failed, List<String> failures) {
    }

    static void section(String s) { System.out.println("\n== " + s); }

    static void ok(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  PASS " + name); } else { fail(name); }
    }

    static void fail(String text) { failed++; failures.add(text); System.out.println("  FAIL " + text); }

    static void api(String name, int status, Runnable r) {
        try {
            r.run();
            fail("(no error raised) " + name);
        } catch (ApiException e) {
            boolean good = e.getStatus().value() == status;
            if (good) { passed++; System.out.println("  PASS " + name + " -> " + status + " \"" + e.getMessage() + "\""); }
            else fail(name + " expected " + status + " got " + e.getStatus().value() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            fail(name + " crashed: " + e);
        }
    }

    /** one complete in-memory "application": repositories + real services wired like Spring would */
    static class App {
        final UserRepository users = Repos.create(UserRepository.class);
        final DonationRepository donations = Repos.create(DonationRepository.class);
        final FoodRequestRepository foodRequests = Repos.create(FoodRequestRepository.class);
        final NotificationRepository notificationRepo = Repos.create(NotificationRepository.class);
        final NgoWeeklyStatRepository weekly = Repos.create(NgoWeeklyStatRepository.class);
        final BookListingRepository bookListings = Repos.create(BookListingRepository.class);
        final BookRequestRepository bookRequests = Repos.create(BookRequestRepository.class);
        final MedicineDonationRepository medicines = Repos.create(MedicineDonationRepository.class);
        final MedicineRequestRepository medicineRequests = Repos.create(MedicineRequestRepository.class);
        final MediPolicyRepository policies = Repos.create(MediPolicyRepository.class);
        final MediAuditRepository audit = Repos.create(MediAuditRepository.class);
        final EwasteRecordRepository records = Repos.create(EwasteRecordRepository.class);
        final EwastePickupRepository pickups = Repos.create(EwastePickupRepository.class);
        final ModuleSettingRepository settings = Repos.create(ModuleSettingRepository.class);
        final UserModuleAccessRepository overrides = Repos.create(UserModuleAccessRepository.class);
        final NgoProfileRepository ngoProfiles = Repos.create(NgoProfileRepository.class);

        final Fakes.Enc encoder = new Fakes.Enc();
        final Path tmp;
        final ModuleAccessService modules = new ModuleAccessService(settings, overrides, users);
        final NotificationService notifications = new NotificationService(notificationRepo);
        final AuthService auth = new AuthService(users, encoder, modules);
        final FileStorageService files;
        final DonationService donationService;
        final RequestService requestService = new RequestService(foodRequests, donations, weekly, notifications);
        final BookService bookService;
        final MediService mediService = new MediService(medicines, medicineRequests, policies, audit, users, notifications);
        final NgoDocumentStorage ngoStorage;
        final NgoService ngoService;
        final ApprovalService approvals = new ApprovalService(donations, bookListings, medicines, ngoProfiles, users, notifications);
        EcoService eco;

        App(String ecoUrl) throws Exception {
            tmp = Files.createTempDirectory("sevasetu-run");
            files = new FileStorageService(tmp.resolve("uploads").toString());
            ngoStorage = new NgoDocumentStorage(tmp.resolve("private").toString());
            donationService = new DonationService(donations, foodRequests, files, users, notifications, modules);
            bookService = new BookService(bookListings, bookRequests, users, notifications, files, modules);
            ngoService = new NgoService(ngoProfiles, users, encoder, ngoStorage, notifications, auth);
            eco = new EcoService(records, pickups, users, notifications, new EcoAiClient(new ObjectMapper(), ecoUrl, ""));
            new ModuleInitializer(settings).run();
            // @Query method of the original FoodShare code: ids of the donations this NGO already requested
            Repos.custom(foodRequests, "findRequestedDonationIds", args -> foodRequests.findAll().stream()
                    .filter(r -> r.getNgo().getId().equals(((User) args[0]).getId())).map(r -> r.getDonation().getId()).toList());
        }

        User user(String name, String email, Role role, boolean verified) {
            User u = new User();
            u.setName(name);
            u.setEmail(email);
            u.setPasswordHash("{enc}pw");
            u.setRole(role);
            u.setOrgType("Test");
            u.setPhone("9811100000");
            u.setVerified(verified);
            return users.save(u);
        }

        User fresh(User u) { return users.findById(u.getId()).orElseThrow(); }
    }

    static final byte[] PDF = "%PDF-1.4 certificate".getBytes();

    public static void main(String[] args) throws Exception {
        Result r = run();
        System.out.println("\n==============================");
        System.out.println("RESULT: " + r.passed() + " passed, " + r.failed() + " failed");
        System.exit(r.failed() == 0 ? 0 : 1);
    }

    public static Result run() throws Exception {
        passed = 0; failed = 0; failures.clear();
        Process py = startPython();
        pythonUp = py != null;
        if (!pythonUp) System.out.println("NOTE: Python forecast service could not be started (python/pip libraries missing?). Forecast checks are skipped.");
        try {
            App a = new App("http://127.0.0.1:5001");
            User admin = a.user("Admin", "admin@foodshare.com", Role.ADMIN, true);
            moduleAccess(a);
            authAndSignup(a, admin);
            ngoRegistration(a, admin);
            foodApproval(a, admin);
            bookShare(a, admin);
            mediShare(a, admin);
            ecoAi(a, admin);
            reports(a, admin);
            seeder();
        } finally {
            if (py != null) py.destroy();
        }
        return new Result(passed, failed, new ArrayList<>(failures));
    }

    // ------------------------------------------------------------------------------------------------------------
    static void moduleAccess(App a) {
        section("Module permissions");
        User donor = a.user("Donor", "d@x.org", Role.DONOR, true);
        User student = a.user("Student", "s@x.org", Role.RECIPIENT, true);
        User hosp = a.user("Hosp", "h0@x.org", Role.HOSPITAL, false);
        ok("donor can use FoodShare", a.modules.statusFor(donor, PlatformModule.FOODSHARE) == ModuleAccessService.Status.AVAILABLE);
        ok("recipient cannot use FoodShare", a.modules.statusFor(student, PlatformModule.FOODSHARE) == ModuleAccessService.Status.NOT_FOR_ROLE);
        ok("unverified hospital needs verification", a.modules.statusFor(hosp, PlatformModule.MEDISHARE) == ModuleAccessService.Status.NEEDS_VERIFICATION);
        api("require() blocks unverified hospital", 403, () -> a.modules.require(hosp, PlatformModule.MEDISHARE));
        a.modules.toggle(PlatformModule.BOOKSHARE);
        ok("module switched off -> coming soon", a.modules.statusFor(student, PlatformModule.BOOKSHARE) == ModuleAccessService.Status.COMING_SOON);
        ok("sign-up role hidden while its module is off", !a.modules.roleAvailable(Role.RECIPIENT));
        a.modules.toggle(PlatformModule.BOOKSHARE);
        ok("module back on", a.modules.statusFor(student, PlatformModule.BOOKSHARE) == ModuleAccessService.Status.AVAILABLE);
        a.modules.setOverride(donor.getId(), PlatformModule.FOODSHARE, false);
        ok("admin can revoke a module for one user", a.modules.statusFor(donor, PlatformModule.FOODSHARE) == ModuleAccessService.Status.REVOKED);
        a.modules.setOverride(donor.getId(), PlatformModule.FOODSHARE, null);
        ok("override removed", a.modules.statusFor(donor, PlatformModule.FOODSHARE) == ModuleAccessService.Status.AVAILABLE);
        ok("approval defaults: food+books on, medishare+ecoai off", a.modules.requiresApproval(PlatformModule.FOODSHARE) && a.modules.requiresApproval(PlatformModule.BOOKSHARE)
                && !a.modules.requiresApproval(PlatformModule.MEDISHARE) && !a.modules.requiresApproval(PlatformModule.ECOAI));
        api("approval toggle refused for MediShare", 400, () -> a.modules.toggleApproval(PlatformModule.MEDISHARE));
    }

    static void authAndSignup(App a, User admin) {
        section("Sign-up / login rules");
        Fakes.Browser b = new Fakes.Browser();
        User asha = a.auth.signup(new SignupRequest("Asha Verma", "asha@x.org", "secret1", "recipient"), b.request);
        ok("recipient signs up and is logged in", asha.getRole() == Role.RECIPIENT && a.auth.requireUser(b.session).getId().equals(asha.getId()));
        api("NGOs cannot use the simple sign-up", 400, () -> a.auth.signup(new SignupRequest("Fake NGO", "fakengo@x.org", "secret1", "ngo"), new Fakes.Browser().request));
        api("nobody can sign up as admin", 400, () -> a.auth.signup(new SignupRequest("Eve", "eve@x.org", "secret1", "admin"), new Fakes.Browser().request));
        api("duplicate email", 409, () -> a.auth.signup(new SignupRequest("Asha 2", "ASHA@x.org", "secret1", "donor"), new Fakes.Browser().request));
        User hosp = a.auth.signup(new SignupRequest("Care Hospital", "hosp@x.org", "secret1", "hospital"), new Fakes.Browser().request);
        ok("hospital sign-up starts unverified", !hosp.isVerified());
        User rec = a.auth.signup(new SignupRequest("Eco Recyclers", "rec@x.org", "secret1", "recycler"), new Fakes.Browser().request);
        ok("recycler sign-up starts unverified", !rec.isVerified());
        a.users.findById(hosp.getId()).get().setVerified(true);
        a.users.findById(rec.getId()).get().setVerified(true);
        api("wrong password", 401, () -> a.auth.login(new LoginRequest("asha@x.org", "nope"), new Fakes.Browser().request));
        ok("login works", a.auth.login(new LoginRequest("asha@x.org", "secret1"), new Fakes.Browser().request).getId().equals(asha.getId()));
        api("recipient is refused on donor-only endpoints", 403, () -> a.auth.require(b.session, Role.DONOR));
    }

    static NgoService.NgoForm ngoForm(String email, String reg, boolean declaration) {
        return new NgoService.NgoForm(email, "secret1", "Hope Foundation", "Trust", reg, 2019, "Meera", "9876543210", "21 Lane", "Delhi", "Delhi",
                "110024", "https://hope.example.org", "Meals and school supplies", null, 1200, declaration);
    }

    static void ngoRegistration(App a, User admin) throws Exception {
        section("NGO registration + admin verification");
        Fakes.Browser b = new Fakes.Browser();
        var dto = a.ngoService.register(ngoForm("hope@x.org", "REG/2019/45", true), new Fakes.File("cert.pdf", "application/pdf", PDF), b.request);
        User ngo = a.users.findByEmailIgnoreCase("hope@x.org").orElseThrow();
        ok("application submitted", dto.status().equals("SUBMITTED") && dto.hasDocument() && ngo.getRole() == Role.NGO && !ngo.isVerified());
        ok("NGO is logged in right after registering", a.auth.requireUser(b.session).getId().equals(ngo.getId()));
        api("unverified NGO is blocked from NGO endpoints", 403, () -> a.auth.require(b.session, Role.NGO));
        StringBuilder got = new StringBuilder();
        for (PlatformModule m : PlatformModule.values()) got.append(m).append("=").append(a.modules.statusFor(ngo, m)).append(" ");
        ok("unverified NGO is locked out of every module it could use [got: " + got.toString().trim() + "]", Stream(PlatformModule.FOODSHARE, PlatformModule.BOOKSHARE, PlatformModule.MEDISHARE).allMatch(m -> a.modules.statusFor(ngo, m) == ModuleAccessService.Status.NEEDS_VERIFICATION)
                && a.modules.statusFor(ngo, PlatformModule.ECOAI) == ModuleAccessService.Status.NOT_FOR_ROLE);
        ok("certificate stored in the PRIVATE folder only", Files.list(a.tmp.resolve("private")).count() == 1 && !Files.exists(a.tmp.resolve("uploads")));
        ok("NGO can open its own document", a.ngoService.myDocument(ngo).exists());
        ok("admin was notified", a.notificationRepo.findAll().stream().anyMatch(n -> n.toString().length() > 0));
        api("duplicate registration number (case-insensitive)", 409, () -> a.ngoService.register(ngoForm("two@x.org", "reg/2019/45", true), new Fakes.File("c.pdf", "application/pdf", PDF), new Fakes.Browser().request));
        api("duplicate email", 409, () -> a.ngoService.register(ngoForm("hope@x.org", "REG/NEW/1", true), new Fakes.File("c.pdf", "application/pdf", PDF), new Fakes.Browser().request));
        api("declaration must be ticked", 400, () -> a.ngoService.register(ngoForm("three@x.org", "REG/3", false), new Fakes.File("c.pdf", "application/pdf", PDF), new Fakes.Browser().request));
        api("certificate is required", 400, () -> a.ngoService.register(ngoForm("four@x.org", "REG/4", true), null, new Fakes.Browser().request));
        api("an .exe renamed to .pdf is refused", 400, () -> a.ngoService.register(ngoForm("five@x.org", "REG/5", true), new Fakes.File("x.pdf", "application/pdf", "MZ-evil".getBytes()), new Fakes.Browser().request));
        ok("failed registrations left no user behind", a.users.findByEmailIgnoreCase("five@x.org").isEmpty() && a.users.findByEmailIgnoreCase("two@x.org").isEmpty());
        long profileId = a.ngoProfiles.findByUser(ngo).orElseThrow().getId();
        ok("admin sees the application", a.ngoService.list("submitted").size() == 1 && a.ngoService.detail(profileId).registrationNumber().equals("REG/2019/45"));
        ok("admin can open the certificate", a.ngoService.document(profileId).exists());
        // reject -> edit -> approve
        api("rejecting needs a reason", 400, () -> a.ngoService.review(admin, profileId, new NgoService.ReviewBody("reject", " ")));
        a.ngoService.review(admin, profileId, new NgoService.ReviewBody("reject", "Certificate is blurry"));
        ok("rejected NGO stays locked", !a.fresh(ngo).isVerified() && a.ngoService.detail(profileId).status().equals("REJECTED"));
        api("rejected NGO still blocked", 403, () -> a.auth.require(b.session, Role.NGO));
        var again = a.ngoService.resubmit(a.fresh(ngo), ngoForm(null, "REG/2019/45", true), null);
        ok("re-submit keeps the old certificate, clears the note", again.status().equals("SUBMITTED") && again.reviewNote() == null && again.hasDocument());
        a.ngoService.review(admin, profileId, new NgoService.ReviewBody("approve", null));
        ok("approved NGO is verified", a.fresh(ngo).isVerified());
        ok("approved NGO now passes the NGO check", a.auth.require(b.session, Role.NGO).getId().equals(ngo.getId()));
        api("a reviewed application cannot be reviewed twice", 409, () -> a.ngoService.review(admin, profileId, new NgoService.ReviewBody("reject", "late")));
        api("approved NGO cannot re-submit", 409, () -> a.ngoService.resubmit(a.fresh(ngo), ngoForm(null, "REG/2019/45", true), null));
        ok("NGO modules are open after approval", a.modules.statusFor(a.fresh(ngo), PlatformModule.FOODSHARE) == ModuleAccessService.Status.AVAILABLE);
        api("document path tricks are refused", 404, () -> a.ngoStorage.load("../../etc/passwd"));
    }

    static DonationRequest food(int days) {
        return new DonationRequest("Cooked Food", 20, "kg", "Veg biryani", "Fresh (Ready to eat)", LocalDate.now().plusDays(days), LocalTime.of(20, 0), "Main road",
                "Delhi", "110005", "9876543210", "NGO pickup", LocalDate.now().plusDays(days), LocalTime.of(14, 0), LocalTime.of(16, 0), null);
    }

    static void foodApproval(App a, User admin) {
        section("FoodShare: donor submissions need admin approval");
        User donor = a.user("Spice Kitchen", "spice@x.org", Role.DONOR, true);
        User ngo = a.users.findByEmailIgnoreCase("hope@x.org").orElseThrow();
        User ngo2 = a.user("Unverified NGO", "unv@x.org", Role.NGO, false);
        int pts0 = a.fresh(donor).getPoints();
        var d = a.donationService.create(donor, food(1), List.of());
        ok("new donation is 'Awaiting approval'", d.status().equals("Awaiting approval"));
        ok("no points before approval", a.fresh(donor).getPoints() == pts0);
        ok("hidden from NGOs", a.donationService.available(ngo, null, null, null).stream().noneMatch(x -> x.id().equals(d.id())));
        ok("hidden from the public list", a.donationService.available(null, null, null, null).stream().noneMatch(x -> x.id().equals(d.id())));
        api("NGO cannot request an unapproved donation", 409, () -> a.requestService.create(ngo, new CreateFoodRequest(d.id(), "hi")));
        api("strangers cannot open it", 404, () -> a.donationService.detail(null, d.id()));
        ok("owner can open it", a.donationService.detail(donor, d.id()).id().equals(d.id()));
        ok("admin sees it in the Food queue", a.approvals.food("pending").size() == 1 && a.approvals.summary().food() == 1);
        ok("admin was notified about the food submission", a.notificationRepo.findAll().stream().anyMatch(n -> n.toString().contains("a") ));
        api("rejecting needs a reason", 400, () -> a.approvals.reviewFood(d.id(), new ApprovalService.ReviewBody("reject", "")));
        a.approvals.reviewFood(d.id(), new ApprovalService.ReviewBody("approve", null));
        ok("approval gives the donor his points", a.fresh(donor).getPoints() == pts0 + d.points());
        ok("approved donation is visible to NGOs", a.donationService.available(ngo, null, null, null).stream().anyMatch(x -> x.id().equals(d.id())));
        ok("verified NGO can now request it", a.requestService.create(ngo, new CreateFoodRequest(d.id(), "we will collect")) != null);
        api("unverified NGO cannot request", 403, () -> a.requestService.create(ngo2, new CreateFoodRequest(d.id(), "hi")));
        api("an approved donation cannot be reviewed again", 409, () -> a.approvals.reviewFood(d.id(), new ApprovalService.ReviewBody("approve", null)));
        // reject path
        var d2 = a.donationService.create(donor, food(2), List.of());
        a.approvals.reviewFood(d2.id(), new ApprovalService.ReviewBody("reject", "Photo does not match"));
        var shown = a.donationService.mine(donor).stream().filter(x -> x.id().equals(d2.id())).findFirst().orElseThrow();
        ok("rejected donation shows 'Rejected' + the reason to the donor", shown.status().equals("Rejected") && "Photo does not match".equals(shown.reviewNote()));
        ok("rejected donation gave no points", a.fresh(donor).getPoints() == pts0 + d.points());
        // cancelling a donation that was never approved must not take points away
        var d3 = a.donationService.create(donor, food(3), List.of());
        int before = a.fresh(donor).getPoints();
        a.donationService.cancel(donor, d3.id());
        ok("cancelling an unapproved donation keeps the points unchanged", a.fresh(donor).getPoints() == before);
        // approval switched off
        a.modules.toggleApproval(PlatformModule.FOODSHARE);
        var d4 = a.donationService.create(donor, food(4), List.of());
        ok("with approval off the donation is live at once", d4.status().equals("Pending") && a.fresh(donor).getPoints() == before + d4.points());
        a.modules.toggleApproval(PlatformModule.FOODSHARE);
    }

    static BookService.BookForm bookForm() {
        return new BookService.BookForm("Oxford Grammar", "Oxford", "English", "Class 9", "English", "Good", 3, "desc", "Delhi", "110005", "12 Street", "9876543210");
    }

    static void bookShare(App a, User admin) {
        section("BookShare: approval, requests, hand-over");
        User donor = a.user("Rahul", "rahul@x.org", Role.DONOR, true);
        User student = a.users.findByEmailIgnoreCase("asha@x.org").orElseThrow();
        User stranger = a.user("Other Student", "o@x.org", Role.RECIPIENT, true);
        var b = a.bookService.create(donor, bookForm(), null);
        ok("listing waits for approval", b.status().equals("Awaiting approval"));
        ok("hidden from students", a.bookService.available(student, null, null, null, null).isEmpty());
        api("cannot be requested before approval", 409, () -> a.bookService.createRequest(student, new BookService.CreateRequest(b.id(), 1, null)));
        api("not even visible by id", 404, () -> a.bookService.detail(student, b.id()));
        ok("admin sees it under Books", a.approvals.books("pending").size() == 1 && a.approvals.summary().books() == 1);
        a.approvals.reviewBook(b.id(), new ApprovalService.ReviewBody("approve", null));
        ok("visible after approval", a.bookService.available(student, null, null, null, null).size() == 1);
        ok("address and phone are hidden before acceptance", a.bookService.detail(student, b.id()).phone() == null && a.bookService.detail(student, b.id()).address() == null);
        api("only 3 copies exist", 400, () -> a.bookService.createRequest(student, new BookService.CreateRequest(b.id(), 9, null)));
        var r = a.bookService.createRequest(student, new BookService.CreateRequest(b.id(), 2, "for school"));
        api("no duplicate request", 409, () -> a.bookService.createRequest(student, new BookService.CreateRequest(b.id(), 1, null)));
        api("a stranger cannot accept", 403, () -> a.bookService.updateStatus(stranger, r.id(), "ACCEPTED"));
        api("the requester cannot accept his own request", 400, () -> a.bookService.updateStatus(student, r.id(), "ACCEPTED"));
        a.bookService.updateStatus(donor, r.id(), "ACCEPTED");
        ok("accepting reserves 2 of 3 copies", a.bookService.detail(donor, b.id()).quantityLeft() == 1);
        ok("contact details unlock for the accepted student", a.bookService.detail(student, b.id()).phone() != null);
        ok("...but not for others", a.bookService.detail(stranger, b.id()).phone() == null);
        api("complete only after accepting (another request)", 400, () -> {
            var r2 = a.bookService.createRequest(stranger, new BookService.CreateRequest(b.id(), 1, null));
            a.bookService.updateStatus(donor, r2.id(), "COMPLETED");
        });
        int pts = a.fresh(donor).getPoints();
        a.bookService.updateStatus(donor, r.id(), "COMPLETED");
        ok("hand-over earns 10 points per copy", a.fresh(donor).getPoints() == pts + 20);
        var s = a.bookService.stats(student);
        ok("student statistics", s.copiesReceived() == 2 && s.myRequests() == 1);
        var b2 = a.bookService.create(donor, bookForm(), null);
        a.approvals.reviewBook(b2.id(), new ApprovalService.ReviewBody("reject", "Not a textbook"));
        ok("rejected listing is closed, not shown", a.bookService.available(student, null, null, null, null).stream().noneMatch(x -> x.id().equals(b2.id()))
                && a.bookService.mine(donor).stream().anyMatch(x -> x.id().equals(b2.id()) && x.status().equals("Rejected") && "Not a textbook".equals(x.reviewNote())));
    }

    static MediService.MedicineForm medi(String name, int days, boolean sealed, String storage, boolean d1, boolean d2, boolean d3) {
        return new MediService.MedicineForm("Tablets/Capsules", name, "500 mg", "Tablet", "B-1", LocalDate.now().plusDays(days), 100, "strips", sealed, storage,
                "LIC-123", null, "Pharmacy block", "Delhi", "9876543210", d1, d2, d3);
    }

    static void mediShare(App a, User admin) {
        section("MediShare: safety rules, admin review, audit trail");
        User hospital = a.users.findByEmailIgnoreCase("hosp@x.org").orElseThrow();
        User ngo = a.users.findByEmailIgnoreCase("hope@x.org").orElseThrow();
        User unverified = a.user("New Hospital", "newhosp@x.org", Role.HOSPITAL, false);
        api("unverified hospital cannot open MediShare", 403, () -> a.modules.require(unverified, PlatformModule.MEDISHARE));
        api("authorisation declaration is required", 400, () -> a.mediService.create(hospital, medi("Paracetamol", 400, true, "Room temperature", false, true, true)));
        api("controlled-substance declaration is required", 400, () -> a.mediService.create(hospital, medi("Paracetamol", 400, true, "Room temperature", true, false, true)));
        api("restricted term 'tramadol' is blocked", 400, () -> a.mediService.create(hospital, medi("TRAMADOL tablets", 400, true, "Room temperature", true, true, true)));
        api("insulin is blocked", 400, () -> a.mediService.create(hospital, medi("Insulin pen", 400, true, "Room temperature", true, true, true)));
        api("short shelf life is refused", 400, () -> a.mediService.create(hospital, medi("Paracetamol", 30, true, "Room temperature", true, true, true)));
        api("unsealed pack is refused", 400, () -> a.mediService.create(hospital, medi("Paracetamol", 400, false, "Room temperature", true, true, true)));
        api("cold chain is refused by default", 400, () -> a.mediService.create(hospital, medi("Paracetamol", 400, true, "Refrigerated (cold chain)", true, true, true)));
        ok("nothing unsafe was stored", a.medicines.count() == 0);
        var m = a.mediService.create(hospital, medi("Paracetamol", 400, true, "Room temperature", true, true, true));
        ok("valid listing waits for admin review", m.status().equals("PENDING_REVIEW"));
        ok("hidden from NGOs until approved", a.mediService.available(ngo, null, null).isEmpty());
        ok("admin sees it in the review queue + summary", a.mediService.reviewQueue(admin).size() == 1 && a.approvals.summary().medicine() == 1);
        api("rejecting needs a reason", 400, () -> a.mediService.review(admin, m.id(), new MediService.ReviewBody("reject", null)));
        a.mediService.review(admin, m.id(), new MediService.ReviewBody("approve", null));
        ok("approved listing is visible to a verified NGO", a.mediService.available(ngo, null, null).size() == 1);
        ok("licence number is hidden from the NGO", a.mediService.available(ngo, null, null).get(0).licenceNumber() == null && a.mediService.available(ngo, null, null).get(0).address() == null);
        api("hospital cannot request its own listing", 400, () -> a.mediService.createRequest(hospital, new MediService.CreateRequest(m.id(), 5, "use", "REG-1")));
        var req = a.mediService.createRequest(ngo, new MediService.CreateRequest(m.id(), 40, "free clinic", "REG/2019/45"));
        api("no duplicate request", 409, () -> a.mediService.createRequest(ngo, new MediService.CreateRequest(m.id(), 1, "x", "REG-2")));
        api("a stranger cannot approve", 403, () -> a.mediService.updateStatus(unverified, req.id(), new MediService.StatusBody("APPROVED", null)));
        api("the requester cannot approve his own request", 400, () -> a.mediService.updateStatus(ngo, req.id(), new MediService.StatusBody("APPROVED", null)));
        a.mediService.updateStatus(hospital, req.id(), new MediService.StatusBody("APPROVED", null));
        ok("approval reserves 40 of 100 units", a.mediService.detail(hospital, m.id()).quantityLeft() == 60);
        ok("pickup details unlock for the approved NGO", a.mediService.detail(ngo, m.id()).address() != null);
        a.mediService.updateStatus(hospital, req.id(), new MediService.StatusBody("COLLECTED", null));
        ok("collected recorded", a.mediService.myRequests(ngo).get(0).status().equals("Collected"));
        ok("every step is in the audit trail", a.mediService.auditTrail().size() >= 6);
        var pol = a.mediService.policyDto();
        ok("policy defaults are conservative", pol.minShelfLifeDays() == 180 && pol.requireAdminReview() && !pol.allowColdChain() && pol.requireSealedPack());
        a.mediService.updatePolicy(admin, new MediService.PolicyBody(200, true, true, false, "Tablets/Capsules,First-aid supplies", "morphine"));
        api("policy change applies at once (shelf life 200)", 400, () -> a.mediService.create(hospital, medi("Paracetamol", 150, true, "Room temperature", true, true, true)));
        api("category removed from the policy is refused", 400, () -> a.mediService.create(hospital, new MediService.MedicineForm("Diagnostic kits", "Kit", null, null, "B", LocalDate.now().plusDays(400), 5, "boxes", true, "Room temperature", "LIC-123", null, "x", "Delhi", "9876543210", true, true, true)));
    }

    /** Starts ecoai/service.py with whichever Python command works (python3, python, py -3). Returns null when it cannot run. */
    static Process startPython() {
        java.io.File dir = new java.io.File("ecoai");
        if (!new java.io.File(dir, "service.py").exists()) dir = new java.io.File("/home/claude/fs/ecoai");
        if (!new java.io.File(dir, "service.py").exists()) return null;
        HttpClient c = HttpClient.newHttpClient();
        for (String[] cmd : new String[][]{{"python3", "service.py"}, {"python", "service.py"}, {"py", "-3", "service.py"}}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true);
                pb.environment().put("ECOAI_PORT", "5001");
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process p = pb.start();
                for (int i = 0; i < 40; i++) {
                    try {
                        if (c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:5001/health")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200) return p;
                    } catch (Exception ignored) { }
                    if (!p.isAlive() && i > 2) break;
                    Thread.sleep(250);
                }
                p.destroy();
            } catch (Exception ignored) { }
        }
        return null;
    }

    static void ecoAi(App a, User admin) {
        section("EcoAI: pickups, records, forecast (real Python service)");
        User donor = a.users.findByEmailIgnoreCase("rahul@x.org").orElseThrow();
        User rec = a.users.findByEmailIgnoreCase("rec@x.org").orElseThrow();
        EcoService.PickupForm form = new EcoService.PickupForm("Laptops & computers", "2 old laptops", 6.5, "Main road", "Delhi", "110005", "9876543210", LocalDate.now().plusDays(3));
        api("weight below 0.1 kg is refused", 400, () -> a.eco.request(donor, new EcoService.PickupForm("Batteries", null, 0.05, "x", "Delhi", "110005", "9876543210", LocalDate.now().plusDays(3))));
        api("past date is refused", 400, () -> a.eco.request(donor, new EcoService.PickupForm("Batteries", null, 2, "x", "Delhi", "110005", "9876543210", LocalDate.now().minusDays(1))));
        api("unknown category is refused", 400, () -> a.eco.request(donor, new EcoService.PickupForm("Cars", null, 2, "x", "Delhi", "110005", "9876543210", LocalDate.now().plusDays(3))));
        var p = a.eco.request(donor, form);
        ok("pickup appears in the recycler's open list without the address", a.eco.open(rec).size() == 1 && a.eco.open(rec).get(0).address() == null);
        a.eco.act(rec, p.id(), new EcoService.PickupAction("SCHEDULED", null, null));
        ok("accepting assigns the recycler and shows the address to him", a.eco.assigned(rec).get(0).address() != null && a.eco.open(rec).isEmpty());
        api("collected needs the weight", 400, () -> a.eco.act(rec, p.id(), new EcoService.PickupAction("COLLECTED", null, null)));
        a.eco.act(rec, p.id(), new EcoService.PickupAction("COLLECTED", null, 7.2));
        ok("collected weight feeds the recycler's records", a.records.findAll().size() == 1 && a.eco.records(rec).get(0).kg() == 7.2);
        api("a collected pickup cannot be cancelled", 409, () -> a.eco.cancel(donor, p.id()));
        api("forecast needs 4 months of data", 400, () -> a.eco.forecast(rec, 6, null, null));
        for (int i = 1; i <= 30; i++) {
            double kg = 200 + 3.0 * (30 - i) + 25 * Math.sin(2 * Math.PI * (YearMonth.now().minusMonths(i).getMonthValue() % 12) / 12.0);
            a.eco.saveRecord(rec, new EcoService.RecordForm(YearMonth.now().minusMonths(i).toString(), "Mobile phones", Math.round(kg * 10) / 10.0));
        }
        api("a record for a future month is refused", 400, () -> a.eco.saveRecord(rec, new EcoService.RecordForm(YearMonth.now().plusMonths(2).toString(), "Batteries", 5)));
        ok("saving the same month again replaces the value", a.eco.saveRecord(rec, new EcoService.RecordForm(YearMonth.now().minusMonths(1).toString(), "Mobile phones", 111.1)).kg() == 111.1
                && a.eco.records(rec).stream().filter(r -> r.month().equals(YearMonth.now().minusMonths(1).toString()) && r.category().equals("Mobile phones")).count() == 1);
        if (pythonUp) ecoForecast(a, admin, rec);
        else System.out.println("  SKIP forecast checks (no Python service)");
        ecoRest(a, donor, rec);
    }

    static void ecoForecast(App a, User admin, User rec) {
        Map<String, Object> f = a.eco.forecast(rec, 6, null, 250.0);
        List<?> rows = (List<?>) f.get("forecast");
        ok("forecast returns 6 months from the real Python service", rows.size() == 6 && f.get("model") != null && f.get("confidence") != null);
        ok("forecast rows carry the pickup plan", ((Map<?, ?>) rows.get(0)).containsKey("pickups_needed"));
        ok("forecast months continue after the history", ((Map<?, ?>) rows.get(0)).get("month").toString().equals(YearMonth.now().toString()) || ((Map<?, ?>) rows.get(0)).get("month").toString().compareTo(YearMonth.now().minusMonths(1).toString()) > 0);
        ok("history is returned for the chart and is not demo data", ((List<?>) f.get("history")).size() >= 30 && Boolean.FALSE.equals(f.get("demoData")));
        ok("admin gets a platform-wide forecast", a.eco.forecast(admin, 3, "Mobile phones", null).get("scope").equals("platform"));
        api("horizon above 24 is refused", 400, () -> a.eco.forecast(rec, 25, null, null));
        api("capacity 0 is refused", 400, () -> a.eco.forecast(rec, 6, null, 0.0));
        api("unknown category is refused", 400, () -> a.eco.forecast(rec, 6, "Cars", null));
        api("Python rejects too little data -> 400 with its message", 400, () -> new EcoAiClient(new ObjectMapper(), "http://127.0.0.1:5001", "").forecast(List.of(Map.of("month", "2025-01", "kg", 1.0)), 3, null));
    }

    static void ecoRest(App a, User donor, User rec) {
        var dash = a.eco.dashboard(rec);
        ok("recycler dashboard has totals and a monthly series", ((Number) dash.get("totalKg")).doubleValue() > 1000 && !((List<?>) dash.get("series")).isEmpty());
        ok("donor dashboard counts recycled kg", ((Number) a.eco.dashboard(donor).get("kgRecycled")).doubleValue() == 7.2);
        EcoAiClient dead = new EcoAiClient(new ObjectMapper(), "http://127.0.0.1:5999", "");
        api("Python service down -> clear 503", 503, () -> dead.forecast(List.of(Map.of("month", "2025-01", "kg", 1.0)), 3, null));
    }

    static void reports(App a, User admin) {
        section("Admin reports");
        Fakes.Browser b = new Fakes.Browser();
        a.auth.startSession(b.request, admin);
        ReportController rc = new ReportController(a.auth, a.users, a.donations, a.bookListings, a.bookRequests, a.medicines, a.medicineRequests, a.audit, a.records, a.pickups);
        Map<String, Object> r = rc.report(b.session);
        ok("report has every module", r.keySet().containsAll(List.of("users", "foodshare", "bookshare", "medishare", "ecoai")));
        Fakes.Browser donorBrowser = new Fakes.Browser();
        a.auth.startSession(donorBrowser.request, a.users.findByEmailIgnoreCase("rahul@x.org").orElseThrow());
        api("reports are admin-only", 403, () -> rc.report(donorBrowser.session));
    }

    static void seeder() throws Exception {
        section("Demo data seeder (fresh database)");
        App a = new App("http://127.0.0.1:5001");
        a.user("Spice Kitchen", "donor@foodshare.com", Role.DONOR, true);
        SevaSetuSeeder seeder = new SevaSetuSeeder(a.users, a.encoder, a.bookListings, a.medicines, a.records, a.pickups, a.ngoProfiles);
        seeder.run();
        ok("demo users created", Stream("rahul@foodshare.com", "recipient@foodshare.com", "hospital@foodshare.com", "recycler@foodshare.com", "hope@foodshare.com").allMatch(e -> a.users.findByEmailIgnoreCase(e).isPresent()));
        ok("demo hospital + recycler are verified, demo NGO application is pending", a.users.findByEmailIgnoreCase("hospital@foodshare.com").get().isVerified()
                && a.users.findByEmailIgnoreCase("recycler@foodshare.com").get().isVerified() && !a.users.findByEmailIgnoreCase("hope@foodshare.com").get().isVerified());
        ok("demo data per module", a.bookListings.count() == 5 && a.medicines.count() == 2 && a.records.count() == 120 && a.pickups.count() == 4 && a.ngoProfiles.count() == 1);
        ok("one pending book + one pending NGO show up for the admin", a.approvals.summary().books() == 1 && a.approvals.summary().ngoApplications() == 1);
        long users = a.users.count();
        seeder.run();
        ok("seeding twice creates no duplicates", a.users.count() == users && a.bookListings.count() == 5 && a.records.count() == 120 && a.pickups.count() == 4);
        User recycler = a.users.findByEmailIgnoreCase("recycler@foodshare.com").get();
        if (pythonUp) {
        Map<String, Object> f = a.eco.forecast(recycler, 6, null, 250.0);
        ok("demo history is enough for a forecast and is flagged as demo", ((List<?>) f.get("forecast")).size() == 6 && Boolean.TRUE.equals(f.get("demoData")));
        System.out.println("      demo forecast model: " + f.get("model") + ", confidence: " + f.get("confidence") + ", backtest: " + f.get("backtest") + ", candidates: " + f.get("candidates") + ", first rows: " + ((List<?>) f.get("forecast")).subList(0, 2) + ", last history: " + ((List<?>) f.get("history")).subList(26, 30));
        }
        User hospital = a.users.findByEmailIgnoreCase("hospital@foodshare.com").get();
        ok("demo medicines are visible to a verified NGO", !a.mediService.available(a.user("N", "n@x.org", Role.NGO, true), null, null).isEmpty());
        ok("demo hospital may use MediShare", a.modules.statusFor(hospital, PlatformModule.MEDISHARE) == ModuleAccessService.Status.AVAILABLE);
    }

    @SafeVarargs static <T> java.util.stream.Stream<T> Stream(T... s) { return Arrays.stream(s); }
}
