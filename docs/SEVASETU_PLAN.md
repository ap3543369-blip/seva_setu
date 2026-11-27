# SevaSetu – architecture review and migration plan

*Connecting Resources. Empowering Lives. — One platform, multiple ways to help.*

Everything in sections 1-2 was read from the code in this repository. Section 5 says exactly what has and has not been tested.

## 1. Existing FoodShare architecture

**Stack:** React 18 + Vite + React Router (+ Three.js on the landing page) · Spring Boot 3.5, Java 17, Spring Data JPA, H2 (default) / MySQL · springdoc (Swagger UI).

**Backend (`com.foodshare`)** – layered, one package per layer:
| Layer | Contents |
|---|---|
| `model` | `User` (table `app_users`), `Donation`, `FoodRequest`, `Notification`, `RewardOffer`, `RedeemedReward`, `ContactMessage`, `Story`, `ImpactSnapshot`, `NgoWeeklyStat`; enums `Role`, `DonationStatus`, `RequestStatus` |
| `repo` | one Spring Data repository per entity |
| `service` | `AuthService`, `DonationService`, `RequestService`, `DashboardService`, `RewardService`, `NotificationService`, `ImpactService`, `AdminService`, `FileStorageService`, `Mapper` |
| `web` | controllers, `ApiException`, `GlobalExceptionHandler`, `SpaController` |
| `config` | `AppConfig` (BCrypt, `/uploads`), `DataSeeder` (demo data when DB is empty) |
| `dto` | `Dtos` (records with Bean Validation) |

**Relationships:** `Donation.donor → User` (required) · `Donation.ngo → User` (set when accepted) · `FoodRequest → Donation` + `User ngo` (unique pair) · `Notification → User` · `RedeemedReward → User` · `NgoWeeklyStat → User`.

**Auth:** session cookie (`HttpSession`, attribute `uid`), BCrypt passwords, **no Spring Security**. Authorisation is done in code: `AuthService.require(session, Role.X)` returns the user or throws 401/403. One user has exactly one `Role` (`DONOR`, `NGO`, `ADMIN`). NGOs start `verified=false`; admins can block users and verify NGOs.

**REST API (verified from the controllers):**
- `/api/auth`: POST signup, login, logout · GET me
- `/api/profile`: GET, PUT, POST password
- `/api/donations`: POST (multipart, up to 4 photos), DELETE `/{id}` (cancel), GET `/mine`, GET `/available` · GET `/api/donations/{id}`
- `/api/requests`: POST, GET `/mine`, PATCH `/{id}/status`
- `/api/dashboard`: GET `/donor`, `/ngo`, `/ngo/chart`
- `/api/rewards`: GET, GET `/mine`, POST `/{code}/redeem`, POST `/reset`
- `/api/notifications`: GET, GET `/unread-count`, POST `/read-all`
- `/api/impact` (GET, `/leaderboard`) · `/api/ngos` · `/api/stories` · POST `/api/contact`
- `/api/admin`: GET `/stats`, `/users`, `/donations`, `/messages` · POST `/users/{id}/block`, `/users/{id}/verify`, `/messages/{id}/handled`

**Frontend (`frontend/src`):** public pages (Landing, Auth, Impact, NGOs, Contact, Donation detail) · donor (dashboard, donate wizard, my donations, rewards) · NGO (dashboard, find food, requests board) · admin (overview, users, verification, donations, messages) · shared (notifications, profile, settings) · `components/` (layouts, charts, Hero3D, ui) · `api.js`, `auth.jsx`.

**New in SevaSetu (all session-cookie authenticated, module checked on every call):**
- `/api/modules`, `/api/public/modules`; admin: `/api/admin/modules`, `/api/admin/users/{id}/modules/{key}`, `/api/admin/reports`
- `/api/books`: POST (multipart), GET `/available`, `/mine`, `/stats`, `/{id}`, DELETE `/{id}`, GET `/{id}/requests`, POST `/requests`, GET `/requests/mine`, PATCH `/requests/{id}/status`
- `/api/medishare`: GET `/policy`, `/stats`, POST `/donations`, GET `/donations/mine`, `/donations/available`, `/donations/{id}`, DELETE `/donations/{id}`, GET `/donations/{id}/requests`, POST `/requests`, GET `/requests/mine`, PATCH `/requests/{id}/status`; admin: `/api/admin/medishare/{donations, donations/{id}/review, policy, audit}`
- `/api/ecoai`: GET `/categories`, `/dashboard`, `/forecast`; donor: POST `/pickups`, GET `/pickups/mine`, DELETE `/pickups/{id}`; recycler: GET `/pickups/open`, `/pickups/assigned`, PATCH `/pickups/{id}`, GET/POST `/records`, DELETE `/records/{id}`

## 2. Reuse vs new

**Reuse as the SevaSetu core (no change needed):** `User`/auth/session, `ApiException` + handler, `Notification*`, `ContactMessage`, `Story`, `FileStorageService` (photos), `Mapper` pattern, admin block/verify, `RewardService` (points work across modules), layouts, `ui.jsx`, charts, `useFetch`, `SpaController`.
**Stays FoodShare-specific:** `Donation`, `FoodRequest`, `NgoWeeklyStat`, donor/NGO dashboards, food impact numbers.
**Needs extending:** `Role` (3 new roles), signup/verification rules, admin pages (modules, per-user access), landing/hub (module launcher), `ImpactService` (later: per-module impact).

**New per module**
| Module | Entities (planned) | Rules to enforce |
|---|---|---|
| BookShare | `BookListing` (title, subject/class, condition, qty, photos, status), `BookRequest` (recipient or NGO → listing, note, status) | donors list, recipients/NGOs request, donor approves, pickup/handover, cancel rules |
| MediShare | `MedicineDonation` (name, batch, expiry, sealed-pack flag, storage, licence ref), `MedicineRequest`, `ComplianceLog` | only verified hospital/pharmacy lists; verified NGO/hospital requests; expiry/safety checks; audit trail |
| EcoAI | `EwasteRecord` (month, category, kg, recycler), `EwastePickup` (donor → recycler) | recycler enters/approves data; forecast only from real records |
| Admin | `ModuleSetting`, `UserModuleAccess` (done), reports | module on/off, per-user grant/revoke, verification queue |

## 3. Permission model

`ModuleAccessService.statusFor(user, module)` returns one of: `available`, `coming_soon` (module switched off), `not_for_role`, `revoked` (admin turned it off for this user), `needs_verification`. Admins always pass. Defaults:

| Role | Modules |
|---|---|
| DONOR | FoodShare, BookShare, EcoAI |
| RECIPIENT | BookShare |
| NGO | FoodShare, BookShare, MediShare |
| HOSPITAL (hospital/pharmacy) | MediShare |
| RECYCLER | EcoAI |
| ADMIN | all |

Verification: NGO, HOSPITAL and RECYCLER accounts start unverified. MediShare (NGO/hospital) and EcoAI (recycler) require verification; FoodShare keeps its existing NGO behaviour (unverified NGOs can browse, requests need verification – enforced in `RequestService`). Every new module service must call `modules.require(user, PlatformModule.X)` first.

## 4. Migration plan (nothing in FoodShare is removed or renamed)

| Phase | Work | Status |
|---|---|---|
| 0 | Code review, this document | done |
| 1 | Roles, module switches, per-user access, hub, admin Modules page, dynamic sign-up, SevaSetu branding | code written |
| 2 | **BookShare**: listings with photos, requests, accept / hand-over flow, points for donors | code written |
| 3 | **EcoAI**: Python forecasting engine + Spring records, pickups, recycler pages, forecast page | engine tested; Spring/React code written |
| 4 | **MediShare**: hospital listings, admin review, safety policy, verified requesters, audit trail | code written (rules are placeholders, see §6) |
| 5 | Admin reports, demo data for every new module, new-role dashboards, unit tests | code written |
| 5b | **Approvals + NGO registration**: donor submissions (food, books) wait for admin approval, per-module approval switch, admin Approvals page by type, NGO registration form with certificate, NGOs locked until verified | code written |
| 6 | Package reorganisation into `common/auth/foodshare/bookshare/medishare/ecoai/admin` | **not done on purpose**: do it with IDE refactoring + a compiler. Existing FoodShare code still lives in `config/model/repo/service/web`; new modules use their own packages. |
| 7 | Hardening before real use: CSRF, rate limits, HTTPS, real password reset, review of MediShare rules, load/security testing | open |

Rule for every phase: existing endpoints and pages keep working. Every module can be switched off by an admin (Admin → Modules); new modules start switched ON (new database), existing databases keep their saved switches.

## 5. Test status (honest)

Environment used for checking: a Java runtime **with the `javac` compiler module** and Python, but **no Maven, no npm, no internet**. So the Spring/Hibernate/Lombok/Jackson jars were replaced by small stand-ins; the project's own code and the Python service ran for real.

| Part | Status |
|---|---|
| Java compile (110 files, main + tests) | Compiles with the real `javac` against stand-ins for the libraries. This found and fixed one real error (an import that shadowed `@Pattern`). |
| Business logic | **141 end-to-end checks pass** (ScenarioSuite): permissions, sign-up rules, NGO registration and verification, food/book approvals, BookShare, MediShare safety rules and audit, EcoAI, reports, demo data. They run the real services on in-memory repositories (which also validate the Spring Data query names). 131 run when Python is missing. |
| EcoAI Python service | 15 unit tests pass. The Java client was run against the real service (success, 400 and 503 paths). Accuracy was measured only on **synthetic** data. |
| Frontend (55 files) | Syntax, undefined names / imports, API paths against the backend (118 calls), image files, date and number helpers in 3 time zones. **Not built with Vite, not opened in a browser.** |
| JUnit tests (36 test methods) | Compile; the 35 Mockito tests were **not executed** (no Mockito here). ScenarioSuiteTest is the same code that passed 141/141. |
| **Not verified** | Real Maven build, Spring start-up (JPA tables, bean wiring), HTTP layer (validation messages, multipart, JSON), `npm run build`, browser behaviour, Docker. |

First things to do on your machine: `mvn -q test-compile && mvn test`, `cd frontend && npm install && npm run build`, `cd ecoai && python -m unittest discover -s tests`, then the manual checks in the README.

## 6. Assumptions and open decisions

- **Recipient** = individual/student who requests books (BookShare only). Food recipients stay NGOs.
- **Hospital and pharmacy are one role** (`HOSPITAL`); the organisation type (`Hospital` / `Pharmacy`) is stored in `orgType`.
- **EcoAI runs as a separate Python process**; Spring calls it over HTTP (`ecoai.url`, optional `ecoai.key`). If it is down, the forecast page says so.
- **Points:** BookShare donors earn 10 reward points per copy handed over (max 200 per hand-over), added to the same balance FoodShare uses.
- **MediShare is safety/legal-sensitive. I am not a lawyer and do not know your jurisdiction.** The defaults in `MediPolicy` are **placeholders**: 180-day minimum shelf life, sealed original packs only, no cold-chain, admin review of every listing, and a blocked-terms list (controlled/narcotic/psychotropic names, vaccines, insulin, cytotoxic). A pharmacist / legal adviser must confirm or change them (Admin → MediShare Control → Safety policy) before real use. Also decide: which licence documents to verify during admin verification, who may receive items, and how long to keep the audit trail. The platform never dispenses advice about dosage or use.
- **"Verify before selling":** I read this as *each donor submission must be approved by the admin before it goes live* (not as "the donor account must be verified first"). Donors themselves are not blocked from logging in. If you meant account-level verification for donors too, say so and it is a small change.
- **NGO registration** collects: organisation name and type, registration number (must be unique), year, people served, focus areas, contact person + mobile, office address, certificate upload and a declaration. Whether these fields and documents are what you legally need to check is a decision for you / your adviser; the platform only stores them and shows them to the admin.
- Distance on FoodShare cards is a demo value (no geocoding). "Forgot password" is a demo button.
- Demo accounts/data are for development only (`sevasetu.seed-demo=false` turns the new demo data off).
