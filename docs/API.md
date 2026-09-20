# Men Care · API v1

Contratto fra il backend (`backend/`) e le due app. Ogni percorso qui sotto è
preceduto da `/v1`. Vale per Android e iOS allo stesso modo: se una delle due
app ha bisogno di qualcosa che qui non c'è, si aggiorna prima questo file.

## Regole generali

- **Formati**: date `2026-09-20`, orari `10:30`, istanti ISO-8601 con fuso
  (`2026-09-20T08:30:00.000Z`). Le date "da muro" (giorno e ora di un
  appuntamento) sono nel fuso del salone, `Europe/Rome`.
- **Denaro**: sempre centesimi interi (`priceCents`, `totalPriceCents`).
- **Giorni della settimana**: `1` lunedì … `7` domenica (ISO-8601). Gli orari
  settimanali sono mappe `{"1": [{"start": "09:00", "end": "13:00"}, …]}`;
  un giorno assente vuol dire chiuso.
- **Identificativi**: UUID come stringhe opache.
- **Autenticazione**: `Authorization: Bearer <accessToken>` su tutto tranne le
  rotte di accesso. L'access token dura 15 minuti, il refresh 30 giorni e
  ruota a ogni uso.
- **Errori**: sempre `{ "error": { "code", "message", "field"? } }`. I codici
  combaciano con `AppError` delle app: `INVALID_CREDENTIALS`,
  `EMAIL_ALREADY_REGISTERED`, `SLOT_NO_LONGER_AVAILABLE`, `NOT_FOUND`,
  `VALIDATION` (con `field`), `FORBIDDEN`, `UNAUTHORIZED`, `CONFLICT`,
  `RATE_LIMITED`, `UNKNOWN`.
- **Ruoli**: `CLIENT`, `STAFF`, `OWNER`. Il controllo è sul server: un
  operatore non può leggere l'agenda di un collega nemmeno chiamando l'API a
  mano, e gli importi non vengono proprio selezionati per il ruolo STAFF.

## Accesso e profilo

| Metodo | Percorso | Ruolo | Note |
|---|---|---|---|
| POST | `/auth/register` | — | `{firstName, lastName, phone, email, password}` → `{user, tokens}` |
| POST | `/auth/login` | — | `{email, password}` → `{user, tokens}` |
| POST | `/auth/social` | — | `{provider: GOOGLE\|APPLE, idToken}` |
| POST | `/auth/refresh` | — | `{refreshToken}` → nuova coppia |
| POST | `/auth/logout` | — | revoca il refresh token |
| POST | `/auth/password/reset-request` | — | risponde sempre 204 |
| POST | `/auth/password/reset` | — | `{token, newPassword}` |
| GET | `/auth/me` | tutti | il profilo dell'account |
| PATCH | `/auth/me` | tutti | `{firstName, lastName, email, phone}` |
| POST | `/auth/me/password` | tutti | `{currentPassword, newPassword}` → nuova coppia |
| GET/PUT | `/auth/me/notification-prefs` | tutti | i tre interruttori del cliente |
| POST | `/auth/me/devices` | tutti | `{token, platform: ANDROID\|IOS}` per le push |
| DELETE | `/auth/me/devices/:token` | tutti | |
| POST | `/auth/me/avatar` | tutti | multipart, campo `file`, max 5 MB → `{avatarUrl}` |
| DELETE | `/auth/me/avatar` | tutti | |

`tokens` = `{ accessToken, refreshToken, expiresIn }` (secondi).

## Listino, squadra e salone

| Metodo | Percorso | Ruolo | Note |
|---|---|---|---|
| GET | `/catalog` | tutti | `{salon, services, operators}` in una sola chiamata |
| GET | `/catalog/services` · `/catalog/operators` | tutti | per aggiornamenti mirati |
| POST · PATCH | `/catalog/services[/:id]` | OWNER | prezzi in centesimi |
| PATCH | `/catalog/services/:id/operators` | OWNER | `{operatorIds}`, sostituzione in blocco |
| POST · PATCH | `/catalog/operators[/:id]` | OWNER | creare un operatore crea anche il suo account |
| PUT | `/catalog/operators/:id/hours` | OWNER | rifiuta se appuntamenti futuri restano fuori orario |
| PUT | `/catalog/operators/:id/services` | OWNER | stesso controllo |
| PUT | `/catalog/salon` | OWNER | orari con turni spezzati |
| GET · POST | `/catalog/operators/:id/holidays` | STAFF (solo sé), OWNER | ferie su più giorni |
| DELETE | `/catalog/holidays/:id` | STAFF (solo sue), OWNER | |

## Agenda

| Metodo | Percorso | Ruolo | Note |
|---|---|---|---|
| GET | `/booking/availability?operatorId=&serviceIds=&date=&ignoreAppointmentId=` | tutti | `{date, slots: ["09:00", …]}`; in modifica `ignoreAppointmentId` libera il posto dell'appuntamento stesso |
| GET | `/booking/days?operatorId=&serviceIds=&from=&to=&ignoreAppointmentId=` | tutti | `{available: [], fullyBooked: []}` per il calendario |
| GET | `/booking/next-availability?serviceIds=` | tutti | prima disponibilità per ogni operatore |
| GET | `/appointments/me` | CLIENT | i propri, passati e futuri |
| GET | `/appointments?operatorId=&date=` | STAFF, OWNER | lo staff vede solo la propria agenda |
| GET | `/appointments/week?weekStart=` | OWNER | agenda settimanale |
| GET | `/appointments/:id` | tutti | solo se è tuo |
| POST | `/appointments` | tutti | `{clientId?, operatorId?, serviceIds, date, time, noteForOperator?, channel?, replacesAppointmentId?}` |
| PATCH | `/appointments/:id/schedule` | tutti | sposta; il cliente resta sulla griglia, staff e titolare no |
| POST | `/appointments/:id/cancel` | tutti | il cliente solo fino a 2 ore prima |
| POST | `/appointments/:id/status` | STAFF, OWNER | `IN_PROGRESS` · `COMPLETED` · `NO_SHOW` |

Gli stati vanno avanti da soli: un lavoro pianificato, ogni minuto, porta un
appuntamento confermato **in corso** all'orario d'inizio e **completato** a fine
servizio. A mano restano `NO_SHOW` — solo a orario d'inizio passato, anche su un
appuntamento già completato, e il cliente riceve una notifica `BOOKING_NO_SHOW` — e
`COMPLETED` su un no-show, per correggere un errore (senza avviso). `IN_PROGRESS` e
`COMPLETED` si possono ancora chiedere a mano prima del lavoro.

`operatorId` assente in `POST /appointments` significa "qualsiasi operatore": la
scelta la fa il server. `replacesAppointmentId` è la **modifica**: nella stessa
transazione il vecchio appuntamento si annulla (dal cliente o dal salone, secondo chi
modifica; il cliente entro le stesse 2 ore dell'annullamento) e il nuovo si crea, così
lo stesso orario si può tenere e, se il nuovo non entra, il vecchio resta com'era. Se l'orario è appena stato preso la risposta è
`SLOT_NO_LONGER_AVAILABLE`, ed è garantita dal database, non dal codice.

## Lista d'attesa

| Metodo | Percorso | Ruolo | Note |
|---|---|---|---|
| GET | `/waitlist/me` | CLIENT | con la posizione in coda, calcolata |
| POST | `/waitlist` | CLIENT | `{date, time?, operatorId?, serviceIds}`; `time` assente = tutto il giorno |
| DELETE | `/waitlist/:id` | CLIENT | |

Quando un posto si libera (annullamento, spostamento, blocco rimosso) il server
avvisa **una** persona in coda, rispettando il suo interruttore "Slot liberi in
lista d'attesa". Il posto non viene bloccato: chi prenota prima lo prende.

## Permessi e assenze

| Metodo | Percorso | Ruolo |
|---|---|---|
| GET | `/blocks?operatorId=&date=` · `/blocks/week?weekStart=` | STAFF (sé), OWNER |
| GET | `/blocks/conflicts?operatorId=&date=&start=&end=` | STAFF, OWNER |
| POST | `/blocks` | STAFF (sé), OWNER |
| DELETE | `/blocks/:id` | STAFF (suoi), OWNER |

## Clienti (CRM)

| Metodo | Percorso | Ruolo | Note |
|---|---|---|---|
| GET | `/crm/clients?query=&segment=&limit=&cursor=` | STAFF, OWNER | paginazione a cursore |
| GET | `/crm/clients/:id` | STAFF, OWNER | `{client, insights, appointments}`: scheda, abitudini (`favoriteOperatorId`, `averageDaysBetweenVisits`) e storico |
| POST | `/crm/clients` | STAFF, OWNER | il cliente creato al telefono o al banco |
| PATCH | `/crm/clients/:id` | STAFF, OWNER | |

Segmenti: `TUTTI`, `FEDELI`, `INATTIVI_60`, `NO_SHOW`, `TOP_SPESA`.

## Gestione (solo titolare)

| Metodo | Percorso | Note |
|---|---|---|
| GET | `/admin/dashboard?period=DAY\|WEEK\|MONTH` | incassi, appuntamenti, no-show, scontrino medio, occupazione, clienti inattivi; più `upcomingDays` — i prossimi 7 giorni da oggi, `{date, occupancyPercent, waitlistCount, closed}`, indipendenti dal periodo |
| GET/PUT | `/admin/notification-settings` | i quattro interruttori del salone |
| GET/POST/DELETE | `/admin/reminder-rules[/:id]` | promemoria a N ore |
| GET/POST/PATCH | `/admin/campaigns[/:id]` | campagne push |
| GET | `/admin/campaigns/reach?segment=` | `{reachable, segmentSize}` contati davvero |
| POST | `/admin/campaigns/:id/send` | mette in coda l'invio, risponde 202 |

## Notifiche

| Metodo | Percorso | Ruolo | Note |
|---|---|---|---|
| GET | `/notifications?limit=&cursor=` | tutti | `{notifications, unreadCount}`, solo le proprie |
| POST | `/notifications/read-all` | tutti | |
| POST | `/notifications/:id/read` | tutti | |

## Cosa calcola il server (e le app non fanno più)

Prima stava tutto nelle app, in due copie che potevano divergere:

- griglia degli slot, preavviso di 30 minuti, unione per "qualsiasi operatore";
- giorni disponibili e giorni pieni del calendario;
- prima disponibilità di ogni operatore;
- posizione in lista d'attesa e chi avvisare quando un posto si libera;
- statistiche del cliente (visite, operatore preferito, cadenza);
- avanzamento degli stati degli appuntamenti (in corso, completato);
- KPI del titolare, occupazione per operatore e dei prossimi 7 giorni;
- destinatari raggiungibili di una campagna;
- testi delle notifiche.
