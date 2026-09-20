# CLAUDE.md — Antonio De Vito · Men Care

Booking app for a single-location barbershop. Two native apps with the same feature set — Android (Kotlin) and iOS (SwiftUI) — and one shared backend in `backend/` (Fastify + TypeScript + PostgreSQL, dockerized) that both apps talk to.

Italian translations for the team exist (`CLAUDE.it.md`, `docs/FEATURES.it.md`); the English files are authoritative — keep translations in sync when either changes.

**Read `docs/FEATURES.md` before implementing anything** — it is the functional spec of the apps as implemented: screens, form fields and validation, business rules, open points (§9) and the domain model the backend schema derives from (§10). **The implemented UI is the definitive reference**: when the UI changes, `docs/FEATURES.md` and `docs/FEATURES.it.md` change with it, in the same commit. `design/mockup.dc.html` (27 screens) is the original visual reference the apps were built from — where it differs from the apps, the apps win. Brand assets sit alongside it in `design/` (favicon.png, logo-lockup.png, logo-mark.png, logo-mark-dark.png).

**Read `docs/API.md` before touching anything that crosses the network** — it is the endpoint contract between `backend/` and the two apps. If an app needs something the contract does not have, `docs/API.md` changes first.

## Repository layout

```
android/    the Android app — Gradle root, every module below lives here
backend/    the API — Fastify + TypeScript on PostgreSQL, dockerized; schema in
            migrations/, one folder per area in src/modules/ (see backend/README.md)
ios/        the SwiftUI app — XcodeGen project (`xcodegen generate`), same feature set
            as Android, same backend
design/     mockup.dc.html + brand assets, shared by both apps
docs/       FEATURES.md — functional spec of the apps; API.md — the endpoint contract
```

Gradle commands run from `android/`, not from the repo root (`cd android && ./gradlew …`).
Backend commands run from `backend/` (`cd backend && npm run dev`).

## Delivery phases — IMPORTANT

1. **Phase 1 (done): the two client apps, no backend.** Android first, with the SwiftUI app in `ios/` kept as a port of it — a change to one belongs in the other. Build against a local fake data layer (in-memory repositories seeded with realistic demo data matching the mockup: operators Antonio/Luca/Giulia/Sara, the service catalogue with real durations/prices, sample appointments). Goal, reached: the owner can install the APK — or run the iOS app — and click through every screen of all three roles.
2. **Phase 2 (current): the real backend.** It lives in `backend/` (Fastify + TypeScript + PostgreSQL): schema in `backend/migrations/0001_init.sql`, endpoint contract in `docs/API.md`, server-side rules in §9b of `docs/FEATURES.md`. The apps are moving from the fake repositories to network-backed ones behind the same repository interfaces; the fakes stay as test fixtures. Whatever the apps used to compute for themselves (slots, availability, queue positions, stats, KPIs) is now the server's job.

## Product summary

Three roles behind one login (redirect by role after auth):
- **Cliente**: home with next appointment, nearest free slots and one-tap rebook; 4-step booking wizard (operator → services → date/slot → summary); waitlist — "Avvisami" on a fully booked day, or the exact time lost while confirming — with live queue position; appointments (upcoming, past, waitlist) with edit/cancel; profile with personal data, salon hours, password change and notification prefs.
- **Operatore (staff)**: own day agenda, time off ("Ferie e permessi") with conflict resolution, appointment detail (client note, call, mark-completed), manual booking, clients list, profile.
- **Titolare (admin)**: KPI dashboard (revenue, appointments, no-show, avg ticket, occupancy, top services, inactive clients), daily multi-operator agenda with drag-and-drop, manual booking and time off, services (duration, price, operator eligibility), operators (creation with STAFF account, read-only detail), notification rules, opening hours, push campaigns with segments, client CRM, profile.
- **All roles**: in-app notifications page behind the header bell.

Full details, business rules, and domain entities: `docs/FEATURES.md`.

## Android app

### Stack
- Kotlin, **Jetpack Compose + Material 3**, single-activity, Compose Navigation (one nav graph per role area + auth graph).
- **MVVM with unidirectional data flow**: ViewModel + `StateFlow`, immutable UI state data classes, sealed classes/interfaces for UI events.
- **Hilt** for DI. **Coroutines/Flow** everywhere; no RxJava.
- kotlinx.serialization. Coil for images.
- Networking: Retrofit + OkHttp against `/v1` (`docs/API.md`), JSON via kotlinx.serialization. The network repositories are being written now; until a repository is switched over, its fake stays in place and the UI does not change.
- minSdk 26, target latest stable SDK. Version catalog (`libs.versions.toml`) for all dependencies.

### Module structure (multi-module Gradle from the start — this is the "easily modifiable" requirement)

The Gradle root is `android/`; every path below is relative to it.

```
:app                      — entry point, navigation wiring, role routing
:core:designsystem        — theme, typography, colors, reusable components
:core:model               — domain models (pure Kotlin, no Android deps)
:core:data                — repository INTERFACES + fake implementations (Phase 1)
                            + network implementations (Phase 2)
:core:common              — utils, result wrappers, dispatchers
:core:ui                  — screens shared by several role areas (clients list
                            + client sheet, used by staff and admin; notifications
                            page, used by all three)
:feature:auth             — splash, login, registration, password recovery
:feature:client           — home, booking wizard, appointments, profile
:feature:staff            — agenda, time off (ferie e permessi), appointment detail,
                            manual booking, profile
:feature:admin            — dashboard, agenda, manual booking, time off,
                            services, operators, notification settings,
                            opening hours, campaigns, owner profile
```
Features depend on `:core:*` only, never on each other. All data access goes through repository interfaces in `:core:data` — that is the seam the backend plugs into: swapping a fake for its network implementation must not touch a ViewModel or a screen. Never let a ViewModel touch a data source directly.

### Design system (from mockup — adapt iOS mockup to Android/Material conventions, keep the brand look)
- Colors: background `#FDFDFD`, surface/secondary `#EBEBEA`, accent "Olive Wood" `#867357`, near-black `#000006` / `#000004` for dark bands and text. Dark headers on light bodies is the signature layout.
- Typography: **Cormorant Garamond** (display/headlines, serif) + **Jost** (body/UI, sans). Bundle fonts as resources.
- Define everything as Material 3 theme tokens in `:core:designsystem`; no hardcoded colors/sizes in feature code.
- No "Sign in with Apple" on Android: the login screen offers Google only. `SocialProvider.APPLE` stays in the shared contract for the iOS app.
- UI language: **Italian** (default `values/strings.xml` in Italian). All user-facing text in string resources — no hardcoded strings — so future locales are trivial.
- Replace iOS patterns with Android equivalents: system back + top app bar, Material bottom navigation bar, bottom sheets for the block/manual-booking sheets, Material date pickers where sensible (custom calendar for the booking step to match slot-availability display).
- Tablets (iPad and Android tablets) run full screen in every orientation; phones stay portrait. Dark bands, bars and backgrounds span the full width while content stays centered within the shared readable width (640 — `readableWidth()` / `ReadableMaxWidth` in the design system). Only the owner's agenda uses the whole width.

### Security (applies from Phase 1)
- Tokens/credentials only in Jetpack Security (`EncryptedSharedPreferences`/DataStore + Android Keystore). Never in plain SharedPreferences, never logged.
- No secrets/API keys committed to the repo; use `local.properties`/BuildConfig injection.
- HTTPS only (`usesCleartextTraffic=false`), network security config from day one.
- R8/ProGuard enabled for release builds.
- Role checks in navigation: a client build of the nav graph must not be able to reach staff/admin destinations.
- Validate all user input client-side (and later server-side; never trust the client).

### Testing & quality
- Unit tests for ViewModels and business logic (JUnit, Turbine for Flow), especially slot-generation and cart-total logic.
- Fake repositories double as test fixtures.
- ktlint or detekt from the start; warnings are errors in CI-ready config.

## Backend

Lives in `backend/`, alongside `android/` and `ios/`. `backend/README.md` explains how to run
it; `docs/API.md` is the contract; `docs/FEATURES.md` §9b lists the rules it enforces.

- **Fastify 5 + TypeScript**, **PostgreSQL 16**, both in Docker (`docker-compose.yml`: api + db + volumes). `npm run dev` + a local Postgres is enough day to day.
- Schema in numbered SQL migrations (`backend/migrations/0001_init.sql`), applied once by `npm run migrate`; `npm run seed` loads the demo salon. No ORM writes the schema.
- Layout: `src/config` (env, validated at boot), `src/db` (pool, transactions, JSON shapes), `src/lib` (errors, validation, time, slot engine, texts), `src/plugins` (auth + roles, error handler), `src/services`, `src/modules/<area>`, `src/jobs`.
- Auth: JWT access 15 min + refresh 30 days with rotation and reuse detection; **argon2id** hashing; Google/Apple sign-in (Apple only ever reaches the API from iOS); password-reset tokens single-use, 30-min expiry.
- **RBAC on every route, and in the query**: CLIENT sees only its own rows, STAFF only its own agenda and no amounts (money columns are not even selected for STAFF), OWNER the whole salon.
- Request and response validation with zod (`fastify-type-provider-zod`); the shared rules live in `src/lib/validation.ts` and are mirrored by both apps' forms.
- Rate limiting (@fastify/rate-limit), helmet, CORS locked to the app origins.
- **Availability is computed server-side and a booking re-checks it inside the transaction**; on top of that an exclusion constraint on `appointments` makes overlapping appointments impossible, so a lost race answers `SLOT_NO_LONGER_AVAILABLE`.
- Time: every instant is `timestamptz` in UTC; wall-clock day and hour are converted at the edge in `src/lib/time.ts`; the salon works in `Europe/Rome`. Money is always integer cents.
- Client counters (visits, spend, no-shows) are a view over the appointments, never stored columns.
- Push: `PUSH_PROVIDER=log` by default (the in-app notification is still written); FCM/APNs delivery is completed in `src/services/push.ts`. Scheduled work (reminders, waitlist sweeps, campaigns) runs on pg-boss, so Postgres stays the only stateful service.
- API versioned under `/v1`. Entities: §10 of `docs/FEATURES.md`.
- Structured logging (pino); no personal data in logs.
- `npm run typecheck` and `npm test` (vitest) must pass before a change lands.

## Conventions

- Conventional Commits.
- Small, focused PR-sized changes; keep the build green at every step.
- When the mockup and Android platform conventions conflict, follow Android conventions but preserve brand colors/typography/spacing character.
- Anything that identifies the business must stay replaceable at build time: colors, palette, fonts, business name, logo and the other brand assets are configuration, not literals scattered through the code.
- Every change lands on Android **and** iOS, and on every role's version of a shared screen, together with `docs/FEATURES.md` + `docs/FEATURES.it.md` (and `docs/API.md` when the contract moves), in the same commit.
- Open points listed in `docs/FEATURES.md` §9 (payments, the walk-in channel, buttons with no behaviour yet…): ask the user before inventing policy; for pure UI error states, implement sensible defaults. The rules the backend already settled are §9b, not open.
