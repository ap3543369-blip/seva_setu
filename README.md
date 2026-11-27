# SevaSetu – Connecting Resources. Empowering Lives.

Multi-module social-welfare platform (FoodShare · BookShare · MediShare · EcoAI). **FoodShare is complete; the other modules are being added phase by phase** – see [docs/SEVASETU_PLAN.md](docs/SEVASETU_PLAN.md) for the architecture review, plan and exact test status.

## FoodShare module

Surplus-food donation platform. Donors post leftover food, NGOs request and collect it, admins verify NGOs and moderate the platform, donors earn reward points.

**Stack:** React 18 + Vite + React Router + Three.js (frontend) | Java 17, Spring Boot 3.5, Spring Data JPA, H2 / MySQL (backend)

## Run (production style – one server on :8080)
Needs JDK 17+, Maven, Node 18+.
```bash
./run.sh            # Windows: run.bat  (also starts the EcoAI Python service)
```
This builds the React app into `src/main/resources/static` (`cd frontend && npm install && npm run build`) and then starts Spring Boot (`mvn spring-boot:run`).
Open http://localhost:8080 . API docs: http://localhost:8080/swagger-ui.html

## Run (development, hot reload)
```bash
mvn spring-boot:run          # terminal 1  -> API on :8080
cd frontend && npm install && npm run dev   # terminal 2 -> http://localhost:5173 (proxies /api and /uploads)
```
Docker (MySQL): `docker compose up --build` (the Dockerfile builds the React app too; the EcoAI Python service is not in Docker yet).

## Demo logins
| Role | Email | Password |
|---|---|---|
| Donor (food) | donor@foodshare.com | donor123 |
| Donor (books / e-waste) | rahul@foodshare.com | donor123 |
| NGO | ngo@foodshare.com | ngo123 |
| Recipient (student) | recipient@foodshare.com | recipient123 |
| Hospital / pharmacy | hospital@foodshare.com | hospital123 |
| Recycler | recycler@foodshare.com | recycler123 |
| Admin | admin@foodshare.com | admin123 |
| NGO waiting for approval (demo application) | hope@foodshare.com | hope123 |

The login page has one-click buttons for donor, NGO and admin. NGO, hospital and recycler sign-ups must be verified by an admin first (hospital/recycler demo accounts are pre-verified). Demo data for every module is created on first start (`sevasetu.seed-demo=false` disables the new modules' demo data). All demo passwords must be changed or removed before real use.

## Approvals and NGO registration (how access is controlled)
- **Donor submissions need the admin:** a new food donation or book listing is saved as **"Awaiting approval"**, is **not visible** to NGOs / students and cannot be requested. The admin sees it under **Admin → Approvals**, split by type (NGO applications · Food · Books · Medicines), with who sent it, their contact details and photos, and approves or rejects it (a reason is required for a rejection and is sent to the donor). Donors get their reward points only after approval. The admin also gets a notification and a counter badge on *Approvals* for every new submission. Admin → Modules has a switch per module to turn this approval requirement off (default: on for FoodShare and BookShare; MediShare always uses its own review).
- **NGOs register with a proper form:** `/register/ngo` (4 steps: login details → organisation details → contact & address → registration certificate + declaration). The plain sign-up refuses NGOs. After submitting, the NGO can log in but only sees an **"under review"** screen: no browsing, no requests, no modules (also enforced by the backend on every NGO endpoint and module). The admin opens the application (all details + the uploaded certificate), then **approves** or **requests changes** with a note; a rejected NGO edits and re-sends the form. As soon as it is approved the NGO's screen opens by itself.
- Certificates are stored in `private-docs/` (outside the public `/uploads` folder), type-checked by their first bytes (PDF / PNG / JPG, max 5 MB) and can only be opened by the NGO itself or an admin.
- Hospitals and recyclers still use the simple sign-up + admin verification (a registration form like the NGO one can be added the same way).

## Modules and pages
**Public:** landing (3D globe hero, four modules, live food feed, rewards, leaderboard, stories, FAQ) · login / sign-up (six account types) · impact · partner NGOs · contact · donation details · SevaSetu hub (`/hub`)

**FoodShare** (donor, NGO, admin): dashboards, 4-step donate wizard with photos, requests board, rewards, certificates, admin users / verification / donations / messages.

**BookShare** (`/books`): donors list books with photos · recipients and NGOs browse, request copies · donors accept, decline, mark handed over (earn points) · contact details unlock only after acceptance.

**MediShare** (`/medishare`): verified hospitals/pharmacies list items after automatic safety checks (declarations, expiry, sealed pack, blocked terms, no cold chain by default) · admin reviews every listing, can reject or suspend · verified NGOs/hospitals request with a registration number · donor approves → pickup details unlock · every step goes to an audit trail · admin edits the safety policy.

**EcoAI** (`/ecoai`): donors request e-waste pickups · recyclers accept, schedule, record collected kg (feeds their history) and can enter past months · forecast page (3–24 months ahead, optional pickups-needed plan, CSV) powered by the Python service · admin sees a platform-wide forecast.

**Admin:** overview, users (+ per-user module grant/revoke), verification (NGO / hospital / recycler), **Modules** on/off, **MediShare Control**, **Reports** (all modules, CSV), messages.

## Project layout
```
frontend/                React app (Vite)
  src/pages/             Landing, AuthPage, Impact, ... + donor/ ngo/ admin/
  src/components/        Layouts, Hero3D (Three.js), TiltCard, charts, ui, FoodCard
  src/api.js auth.jsx    REST client, session context
  public/assets/         images, logos, gift-card icons
src/main/java/com/foodshare   config/ model/ repo/ dto/ service/ web/   (web/SpaController lets React Router deep links work)
src/main/resources/static     <- generated by `npm run build` (git-ignored)
```

## EcoAI forecasting engine (Python)
```bash
cd ecoai && pip install -r requirements.txt
python3 -m unittest discover -s tests     # 15 tests
python3 forecast.py                       # demo on synthetic data
python3 service.py                        # HTTP service on 127.0.0.1:5001 (ECOAI_PORT / ECOAI_KEY optional)
```
`POST /forecast {"history":[{"month":"2025-01","kg":420},...], "horizon":6, "pickup_capacity_kg":250}` returns the chosen model, backtest error, a forecast with a rough range and (optionally) pickups needed. Spring Boot calls it through `EcoAiClient` (`ecoai.url`, `ecoai.key` in application.properties; `ECOAI_URL` / `ECOAI_KEY` env vars in Docker). `run.sh` starts it for you.

## Tests
```bash
cd ecoai && python -m unittest discover -s tests     # 15 Python tests (use python3 on Linux/macOS)
mvn test                                             # 36 JUnit tests, including ScenarioSuiteTest (below)
```
`ScenarioSuiteTest` runs **141 end-to-end checks** of the real services (sign-up rules, module permissions, NGO registration + admin verification, donor approvals for food and books, BookShare, MediShare safety rules, EcoAI pickups/records, admin reports, demo data) against in-memory repositories. If Python and `ecoai/requirements.txt` are installed it also talks to the real forecast service; otherwise those checks are skipped (131 checks run).

## Manual checks after your first build
1. FoodShare still works end to end: donor donates → NGO requests → accepts → delivers.
2. Sign up as recipient → open BookShare → request a book as `recipient@…`, accept it as `rahul@…`, mark handed over.
3. Log in as `hospital@…`, donate "Paracetamol" (needs the three declarations; try "Tramadol" → blocked) → log in as admin → MediShare Control → approve → log in as `ngo@…` → request it → hospital approves.
4. Start the EcoAI service, log in as `recycler@…` → Forecast → Run (uses 30 months of generated demo data and says so).
5. Admin → Modules: switch BookShare off → recipients see it as "Coming soon"; admin → Users → Modules: revoke a module for one user.
6. Admin → Reports shows counts for all modules.
7. Approvals: as `donor@…` donate food → it shows "Awaiting approval" and is missing from the NGO list → as admin open Approvals → Food → approve → it now appears for NGOs. Try Reject (reason required) and check the donor sees the reason.
8. NGO registration: open `/register/ngo`, register a new NGO with a PDF → you only see the "under review" page → as admin open Approvals → NGO applications → open the certificate → Approve → the NGO's page opens by itself. Also try "Request changes" and the "Edit & send again" path, and log in as `hope@…` (demo application).
9. Turn off Admin → Modules → "Admin approval" for FoodShare → a new food donation is live immediately.

## What was verified, and what was not
Verified before delivery (in the environment I had, which has a Java runtime with the `javac` compiler and Python, but no Maven, npm or internet):
- All 110 Java files (main + tests) compile with the real `javac` against small stand-ins for Spring, JPA, Lombok and Jackson (this catches type errors, wrong constructors, missing methods, checked exceptions; it caught a real error in `NgoService`).
- The scenario suite above: **141 of 141 checks pass** (131 without Python).
- Derived Spring Data query names match entity properties, there are no duplicate URL mappings or constructor cycles, every one of the 118 frontend API calls matches a backend endpoint, and every referenced image exists.
- EcoAI: 15 Python tests pass; the Java client was run against the real Python service (success, "too little data" error and "service down" error).
- Frontend: syntax of all 55 files, no undefined names or missing imports, date/number helpers tested in three time zones.

**Not verified** (it needs your machine): the real Maven build with the real Spring/Hibernate/Lombok jars; application start-up (JPA table creation, bean wiring); the HTTP layer (controllers, validation messages, multipart upload, JSON); `npm install && npm run build`; the screens in a browser; Docker. The `mvn` and `npm` commands are the first thing to run; if anything fails, copy the error text and it can be fixed quickly.

## Troubleshooting
- **Java:** needs JDK 17+ (`java -version`). **Node:** 18+ (`node -v`). **Port 8080 busy:** stop the other program or set `server.port` in `application.properties`.
- **Windows:** use `python` (or `py -3`) instead of `python3`; `run.bat` already does.
- **Blank page / 404 at http://localhost:8080:** the React app is built into `src/main/resources/static`; run `cd frontend && npm install && npm run build` (or just `run.bat` / `./run.sh`).
- **Forecast page says "service is not reachable":** start the EcoAI service (`cd ecoai && python service.py`) after `python -m pip install -r requirements.txt`.
- **You ran an older version of this project before:** the old H2 database in `./data` has a `role` column that only allows the old roles, so signing up as recipient / hospital / recycler can fail. Stop the app and delete the `data` folder once (demo data is created again), or in MySQL run `ALTER TABLE app_users MODIFY role VARCHAR(20) NOT NULL;`. Demo-data problems never stop the app from starting; they are only logged.
- **Photo / certificate upload fails:** files are limited to 5 MB (`spring.servlet.multipart.max-file-size`).
- **NGO cannot see anything after registering:** that is intended until an admin approves it under Admin → Approvals → NGO applications.

## Notes
- Distance on donation cards is a demo value (no geocoding). Demo data is created only when the database is empty; delete `./data` to reseed.
- Change the demo passwords before any real deployment.
#   s e v a _ s e t u  
 