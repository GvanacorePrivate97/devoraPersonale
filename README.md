# Antonio De Vito · Men Care

Booking app for a single-location barbershop. One repository for every platform: the
Android app in `android/`, the iOS app in `ios/`, the API in `backend/`; `docs/` and
`design/` are shared.

Two complete native apps with the same features — Android (Kotlin, Jetpack Compose,
Material 3) and iOS (SwiftUI, see `ios/README.md`) — and one backend (Fastify + TypeScript
+ PostgreSQL) that serves both. The apps are being moved from their in-memory fake data
layer onto the API; the repository interfaces they call do not change, so the screens stay
as they are.

Functional spec of the apps: `docs/FEATURES.md` (§9b: what the server decides) · endpoint
contract: `docs/API.md` · original visual reference: `design/mockup.dc.html`.

## Build the Android app

The Gradle root is `android/` — run every command from there.

```bash
cd android
./gradlew :app:assembleDebug      # debug APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # minified R8 release (unsigned)
./gradlew test detekt             # unit tests + static analysis
```

Requirements: JDK 17+, Android SDK 37 (`android/local.properties` → `sdk.dir`).
The iOS app: `ios/README.md` (XcodeGen, macOS only).

## Run the backend

Everything in one command, API + database:

```bash
cd backend
JWT_ACCESS_SECRET=dev-secret-at-least-32-characters-long \
JWT_REFRESH_SECRET=dev-refresh-at-least-32-characters-long docker compose up --build
docker compose exec api node dist/scripts/migrate.js && docker compose exec api node dist/scripts/seed.js
```

The API answers on `http://localhost:3000/v1`. Without Docker: `cp .env.example .env`,
`npm install`, `npm run migrate && npm run seed && npm run dev`. Details, environment
variables and the rules worth knowing before editing: `backend/README.md`.

## Demo accounts

One login for all roles; the app routes to the role's area after sign-in.
The seed gives all of them the same password: **`mencare2026`**.

| Ruolo | Email |
|---|---|
| Cliente | `marco.esposito@gmail.com` |
| Operatore | `luca.ferrante@mencare.it` |
| Titolare | `antonio@mencare.it` |

Screens still served by the fake data layer accept any password of 6+ characters for the
same three accounts; the Google button signs into the demo client (Sign in with Apple is
iOS-only). Demo appointments — in the seed and in the fakes — are dated relative to today,
so the agendas always look alive.

## Layout

```
android/    Android app (Gradle root, modules below)
backend/    Fastify + TypeScript API on PostgreSQL, dockerized — see backend/README.md
ios/        SwiftUI app (XcodeGen project), same features as Android
design/     mockup.dc.html + brand assets, shared
docs/       FEATURES.md functional spec + API.md endpoint contract, shared
```

## Android modules

```
:app                  entry point, role routing (client can never reach staff/admin graphs)
:core:designsystem    theme (Olive Wood palette, Cormorant Garamond + Jost), shared components
:core:model           pure-Kotlin domain models
:core:data            repository interfaces + fake implementations, SlotEngine, demo seed
:core:common          result wrappers, dispatchers, Italian formatters
:feature:auth         splash, login, registration, password recovery
:core:ui              screens shared by several roles: clients list + sheet, notifications
:feature:client       home, 4-step booking wizard, appointments + waitlist, profile
:feature:staff        day agenda, time off with conflict resolution, appointment detail,
                      manual booking, profile
:feature:admin        dashboard, drag&drop agenda, manual booking, services, operators,
                      notification rules, opening hours, push campaigns, owner profile
```

Key invariant: features depend only on `:core:*`; all data access goes through the
repository interfaces in `:core:data` — the seam the API plugs into, one repository at a
time, without the screens noticing.

## Backend at a glance

```
migrations/   numbered SQL, applied once (0001_init.sql is the whole schema)
scripts/      migrate.ts, seed.ts
src/lib/      slot engine, validation shared with the apps' forms, time (Europe/Rome), errors
src/modules/  one folder per area: auth, catalog, booking, blocks, crm, admin, notifications
src/jobs/     reminders, waitlist sweep, campaign sending (pg-boss)
```

The server owns everything the apps used to work out for themselves: slots and
availability, queue positions, client statistics, KPIs, campaign reach and the text of
every notification.
