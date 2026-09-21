# Antonio De Vito — Men Care · Functional Specification

**Source of truth: the implemented apps.** This document describes what the Android app
(`android/`) and the iOS app (`ios/`) do today, and — from §9b on — the rules the backend
in `backend/` applies behind them. The two apps are identical in features, rules and data;
the few platform differences are listed in §8. `design/mockup.dc.html` is the original
visual reference the apps were built from: where the apps and the mockup differ, the apps
win and this document follows them. The endpoint contract between the apps and the server
is `docs/API.md`.

Three roles behind one login: **Cliente** (client), **Operatore** (staff), **Titolare**
(owner). Business: a single-location barbershop in Napoli. UI language: Italian.

**Economic data belongs to the owner.** Revenue, spend, amounts and totals of past or
upcoming visits are shown only in the Titolare area. The client sees prices only while
booking (service list, bars at the bottom of the wizard, summary, confirmation); the staff
never sees any.

## 1. Authentication

### 1.1 Splash
Brand logo, name ("Antonio De Vito · Men Care"), loading progress, "Powered by Devora".
The app is installed as **"ADV MenCare"**; its icon is the full logo (monogram, "Antonio De
Vito", "Men Care") centered on black, on both platforms.

### 1.2 Login
- Dark header, centered: the full brand logo (monogram, "Antonio De Vito", "Men Care"),
  "Bentornato" and "Il tuo posto in poltrona è a due tap."
- Fields: **Email**, **Password** (show/hide toggle).
- Errors: wrong credentials ("Email o password non corretti"), generic failure.
- "Password dimenticata?" → §1.4. "Non hai un account? Registrati" → §1.3.
- Social login: "Continua con Google"; on iOS also Sign in with Apple (§8).
- One login for every role: after sign-in the app opens the role's area — client Home,
  staff Agenda, owner Agenda.

### 1.3 Registration (clients only)
Creates a CLIENT account and its client record in one step, then signs in.

| Field | Rule |
|---|---|
| Nome | required |
| Cognome | required |
| Numero di telefono | required; fixed `+39` prefix shown beside the field; digits and spaces, at least 8 characters; stored as `+39 <number>` |
| Email | required; valid address; must not be registered already ("Questa email è già registrata") |
| Password | at least 8 characters; strength meter (Debole / Media / Sicura) |
| Conferma password | must match ("Coincide" shown when it does) |
| Terms/privacy checkbox | must be ticked |

Errors stay hidden until the first tap on "Crea account", then update live as the user types.
If the salon already has a client card with that phone number or email and no account on it
— a client created at the counter — registration takes over that card, so the history is
kept; if the card already belongs to an account the answer is "Questo numero è già collegato
a un account". Staff and owner accounts are never self-registered: see §4.6.

### 1.4 Password recovery
- Field: **Email account**. "Invia link di recupero" asks the server for a reset link valid
  **30 minutes** and usable once; the screen then confirms "Link inviato" with the address.
  The answer is the same whether or not the address has an account, so the form never says
  who is registered. Using the link sets the new password and signs every device out.
  Delivery is not wired yet: no email leaves the server (§9).
- "Altre vie": SMS code to the account's masked phone number, or a WhatsApp message to the
  salon — neither sends anything yet (§9).

### 1.5 Logout
"Esci dall'account" at the bottom of every profile page (client, staff, owner).

## 2. Client area — tabs Home · Prenota · Appuntamenti · Profilo

### 2.1 Home
- Dark header: logo on the left, notification bell with unread dot on the right (§5);
  the day's date over the greeting "Ciao <nome>". The lifetime visit counter is not in
  the header: the number belongs to the profile (§2.5), not to the greeting.
- **Next appointment** card: countdown ("tra 4 ore", "oggi"), time, day and total duration,
  operator, services, "Al calendario". No price. With nothing booked: "Nessun appuntamento
  in programma".
- **"Prenota ora"**, centered: opens the wizard.
- **"Primi slot liberi"**: up to 4 chips with the salon's nearest bookable slots on any
  operator (today and the next 7 days, at most 2 per day), sized on the services of the
  last completed visit — or on a featured service for a client with no history. A tap opens
  the wizard on the date step with services, day and time already chosen; the wizard asks
  for availability again first, so if the time was taken in the meantime the day stays
  selected and the client picks another time.
- **"Riprenota in un tap"**: the last completed visit (services, operator, duration) with
  "Riprenota" (opens the wizard on the date step with operator and services chosen) and a
  "Storico" link to the Appuntamenti tab.

### 2.2 Booking wizard (4 steps)
Title "Nuova prenotazione" ("Modifica prenotazione" when editing), step indicator
(Operatore · Servizi · Data · Conferma), "Annulla" at any step returns to the Appuntamenti
tab. On steps 1–3 the bottom bar with the "continue" action rises only once the step has a
valid choice and drops away if the choice is cleared.

1. **Operatore** — "Qualsiasi operatore" ("Il doppio degli orari disponibili") or one of the
   team ("Il team · N operatori"). Each operator card: initials and name, role label
   ("Barbiere"). Operators with no free slot in the next 14 days are dimmed but still
   selectable; the check is made on one of the operator's services, so it means "this
   operator has nothing free", not "nothing free for the services you will choose".
   Changing the operator drops the already-selected services that operator cannot perform,
   and clears the day and the time.
2. **Servizi** — the chosen operator's name with "Cambia" (back to step 1). Flat list of the
   services that operator performs (all of them for "Qualsiasi"), with no categories: name,
   duration, price. Multi-select; the bar sums them ("2 servizi · 75 min · € 22,00").
   Adding or removing a service clears the chosen day and time: the slots depend on the
   total duration.
3. **Data** — month calendar, Monday first, previous month disabled before the current one.
   Day states:
   - *available* (filled, olive dot): at least one slot fits the selected services;
   - *fully booked* (outlined, no dot): the salon is open and the operator(s) work that day,
     but every slot is taken;
   - *not selectable* (faded): past days, salon closing days, days the operator doesn't
     work or is blocked for.

   Selecting an available day shows its slot grid (4 per row, "libero"/"scelto" legend) for
   the **total** duration of the selected services; taken times are simply absent. The bar
   shows day, time, duration and total.

   Selecting a fully booked day replaces the grid with the **"Giornata al completo"**
   page: an animated illustration (a calendar with every day crossed out, a stopwatch
   whose hand keeps turning), the line "Tutti gli slot orari sono già prenotati. Vuoi
   essere avvisato quando si libera un posto?" and the **"Avvisami"** button, pinned to the
   bottom of the screen like the wizard's other bars. "Avvisami" puts the client on the
   waitlist for that day at any time, with the chosen operator (or any) and services
   (§6.4); the button then becomes "Ti avviseremo · N° in fila", with a hint that the
   request is listed in Appuntamenti. Coming back to a day already joined shows the
   joined state.
4. **Riepilogo** — recap card (date, time, duration, operator), per-service breakdown with
   prices and total, optional **"Note per l'operatore"** (max 200 characters, live counter).
   "Conferma prenotazione".

**Slot taken while confirming**: if someone else takes the time in the meantime, the client
goes back to step 3 with fresh availability and the message "Questo orario è appena stato
prenotato. Scegline un altro.", plus a card to join the waitlist for that exact time
("Mettimi in lista" → "N° in fila"). If that was the day's last slot, the day shows the
"Giornata al completo" page instead.

**Edit** ("Modifica" on an upcoming appointment): the wizard reopens on step 1 with
operator, services and note prefilled. While editing, the appointment gives up its own
place, so its current day and time stay selectable. Confirming **replaces** it in one step
on the server: the original is cancelled (by the client) and the new one created together,
so a booking that fails leaves the original untouched.

### 2.3 Confirmation
Dark full-screen page: check mark, "Prenotazione confermata", "Ti aspettiamo <giorno> alle
ore <ora>", recap (operator actually assigned — also when "Qualsiasi operatore" was chosen —
services, duration, total, note if any), "Aggiungi al calendario", "Torna alla Home".

### 2.4 Appuntamenti
Tabs "Prossimi · N" and "Passati · N". The split is by **end** time: an appointment stays
under "Prossimi" until it is over, and the Home keeps showing it as the next one while it
is running.
- **Upcoming** card: date block, time, services, operator and duration — no status badge,
  since every booking is confirmed the moment it is made (§6.2) and a badge that always
  says the same thing informs nobody;
  actions **Modifica** (§2.2) and **Annulla** (confirmation dialog "Annullare
  l'appuntamento?" → cancelled by the client).
- **Lista d'attesa** section, under the upcoming cards when the client is queued: one row
  per request with day, time or "qualsiasi orario", operator or "Qualsiasi operatore",
  "in attesa", queue position ("1° in fila") and **Esci** to leave the queue. Only requests
  still waiting for a day in the future are listed: once the client has been notified of a
  free slot, or the day has passed, the row disappears (§6.4).
- **Past** rows: date block (dimmed when the visit didn't happen), services, time, operator,
  "Riprenota"; cancelled rows say who cancelled ("Annullato dal cliente" / "Annullato dal
  salone"), no-shows say "No-show".
- Empty upcoming tab: short copy, "Prenota ora" and "Riprenota l'ultimo".

### 2.5 Profilo
- Header: initials, full name, "Cliente dal <mese anno> · N visite", **Modifica**/**Salva**.
- **Dati personali**: Nome, Cognome, Email, Telefono — read-only rows, editable fields after
  "Modifica"; "Salva modifiche" saves.
- **Orari del salone**: the opening hours set by the owner (consecutive days with the same
  hours grouped, "chiuso" for closing days) and the salon address.
- **Gestione profilo · Password** → "Cambia password" sheet: Password attuale (required; the
  server checks it and answers "Email o password non corretti" on the field when it is
  wrong), Nuova password (at least 8 characters, at least one letter and one digit, strength
  meter), Conferma nuova password (must match). A successful change signs out every other
  device; the one that made the change stays signed in.
- **Notifiche** (client preferences, per account):
  - "Promemoria appuntamento" (24 ore prima · push) — default ON
  - "Slot liberi in lista d'attesa" — default ON
  - "Promozioni e novità" (marketing opt-in) — default OFF
- "Esci dall'account".

## 3. Staff area (Operatore) — tabs Agenda · Clienti · Profilo

### 3.1 Agenda
- Only the signed-in operator's appointments. Header: photo or initials (tap → Profilo),
  name, role, notification bell (§5).
- Under it the **day bar** (§7b), the same control as the owner's agenda (§4.3):
  previous/next-day arrows, the selected day's date, "Oggi" when the selected day is not
  today, and the six-day strip around it. On an empty day the line "Nessun appuntamento ·
  tocca un orario per prenotare" sits under the bar.
- Day time grid, the same as one column of the owner's agenda (§4.3): hour rail with
  half-hour guides — the rules run under the hour labels too, so rail and grid read as
  one — always shown, even on an empty day ("Nessun appuntamento · tocca un
  orario per prenotare"). Cards sized by duration: client, time range (or "in corso"),
  services, "walk-in" flag; olive while booked or in progress, stone once completed,
  outlined and faded with "No-show" when the client didn't come — kept visible so it can
  be corrected.
  **A card is never too small to read.** Duration sets its height, but it stops at a
  floor that keeps the first row — client and time range — whole, so even the shortest
  service in the catalogue (10 minutes) is legible; and a short appointment takes the
  empty time in front of it, up to the next appointment or block in the column, so it
  can show its services too. It stops growing as soon as they fit: a card never runs
  over what is booked next. The services line appears only where there is room for it,
  on one line or two — text is dropped, never cut through. The same rule sizes the
  owner's agenda cards (§4.3).
  Blocks (§3.2) are a grey band across the column with their label or reason centered
  ("Pausa pranzo", "Ferie"…), the same band as the owner's agenda.
- Tap on a card → appointment detail (§3.3). Tap on a free spot → "Nuovo appuntamento"
  sheet with that day and quarter hour set (§3.5); taps on blocks and on off-shift
  stretches do nothing.
- Floating buttons "Ferie e permessi" (§3.2) and "Nuovo appuntamento" (§3.5). They sit over
  the day grid, so the primary one is **black** and the secondary light with a hairline:
  olive on olive cards would disappear.

### 3.2 Ferie e permessi
Bottom sheet, the same as the owner's (§4.3) without the operator picker (always the
signed-in operator), as tall as its content with the button right under it.
- **Motivo**: Permesso · Pausa · Ferie · Corso.
- **Data** (calendar), **Dalle ore** / **Alle ore** (time wheels); shortcuts "Mezza
  giornata" (09:00–13:00) and "Giornata intera" (09:00–19:00).
- **Conflicts**: appointments overlapping the range are listed ("<cliente> · <ora> ·
  <servizi>. Va spostato o passato a un collega.") and must be resolved before saving:
  "Passa a <collega>" moves the appointment to that colleague at the same time;
  "Proponi altro orario" marks it as handled.
- CTA "Blocca 15:30 – 18:00". A block covers one day; its time disappears from the slots
  offered to clients.

### 3.3 Appointment detail
- Dark header: client initials (Olive Wood on black), name, status ("Confermato", "in
  corso", "Completato ✓", "No-show"), lifetime visits.
- **Modifica** (top bar, only while the appointment is still to come): the "Modifica
  appuntamento" sheet (§3.5) prefilled with client, day, time and services; saving replaces
  the appointment (§6.2) and the agenda comes back.
- Tiles: time range and duration (no price). Services with their durations.
- **Nota del cliente**: the note written in the booking summary, when there is one.
- **Storico visite**: the client's other completed visits (date, services; no amounts).
- Actions: "Annulla appuntamento" (dialog; recorded as cancelled by the salon — the client
  gets a notification, unless the owner switched "Annullamento" off in §4.7), "Chiama"
  (opens the phone dialer with the client's number; disabled when the client has none),
  and, **only on a no-show**, **"Segna completato"** to put it back. Statuses advance by
  themselves (§6.2), so the button is not offered on an appointment that will close itself.
- **"Non si è presentato"**, once the start time has passed — also on an appointment
  already closed as completed: a dialog confirms ("Il cliente riceve una notifica…") and
  the client is notified. A no-show marked by mistake goes back with "Segna completato".

### 3.4 Clienti
The same list and client sheet as the owner's Clienti tab (§4.9), with the spend tile and
the amounts hidden; "Nuova prenotazione" leads back to the agenda.

### 3.5 Nuovo appuntamento (staff)
Bottom sheet, the same as the owner's manual booking (§4.4) with the operator fixed to the
signed-in one and the service list limited to the services that operator performs. Like
every booking, it is made on the 30-minute grid with the same 30 minutes of notice the
client has (§6.1) — the free-form placement is only for moving an existing appointment
(§6.3). Opened from "Modifica" in the appointment detail (§3.3) it becomes "Modifica
appuntamento": the client is fixed, the form is prefilled, the appointment's own time stays
selectable and "Salva modifiche" replaces it (§6.2).

### 3.6 Profilo
Photo or initials (tap → system photo picker; "Rimuovi foto" restores the initials), name
and role; account rows Email and Telefono (read-only); "Esci dall'account".

## 4. Owner area (Titolare) — tabs Dashboard · Clienti · Agenda · Gestione · Profilo
After sign-in the owner lands on the Agenda tab.

### 4.1 Dashboard
Every figure is computed by the server over the chosen period; nothing is padded, and a
period with no work shows zeros.
- Dark header: "Titolare" over the serif title "Andamento", then the period switcher —
  the band carries its own title like every other owner screen.
- Period switcher Giorno · Settimana · Mese (default Giorno): Giorno = today, Settimana =
  Monday to Sunday of the current week, Mese = the calendar month.
- **Incassi**: total of the appointments **marked completed** whose start falls in the
  period, with the trend against the immediately preceding window of the same length
  ("Incassi del mese € 9.840 · +12%"). With nothing in the previous window the trend is not
  invented: it reads 0.
- **Appunt.**: every non-cancelled appointment of the period, all operators, no-shows
  included.
- **No-show %**: no-shows over those appointments, one decimal.
- **Scontrino medio**: revenue divided by the number of completed appointments.
- "Occupazione per operatore" (bars): booked minutes over the minutes the operator could
  actually work in the period — their hours intersected with the salon's, minus blocks,
  with holidays not counted at all — capped at 100%.
- **"Prossimi 7 giorni"** (today included, whatever the period): one column per day with
  the salon's occupancy — booked minutes over the minutes every operator can work that day,
  cancellations and no-shows excluded — olive from 80%, "chiuso" on closing days; an olive
  badge with the number of clients waiting on that day's waitlist ("Avvisami"), and a line
  with the total ("aspettano un operatore al completo": a request waits for a specific
  operator — or any — who is full, while the bar is the whole salon, so a day with
  people waiting can still show room). Below, a full-width gold button opens the push
  campaign (§4.2): "Riempi i giorni vuoti con una campagna" when an open day is under 50%,
  "Invia campagna push" otherwise. It is the only way into the campaign.
- The whole dashboard fits one phone screen; there is no inactive-clients panel (the
  "Inattivi 60gg" segment stays in the campaign).

### 4.2 Campagna push
- "Bozza" badge, **Nome campagna**, reach "N di M raggiungibili": M is the size of the
  segment, N the part of it that can actually be reached — a client with marketing consent
  on their card, an app account, and "Promozioni e novità" on in their profile (§2.5). The
  same count is used for the preview and for the send, so they cannot disagree.
- **Destinatari**: Inattivi 60gg · Tutti · Top spesa (§6.5).
- **Titolo** and **Messaggio** (max 140 characters, counter), tokens "+ Nome cliente"
  (`{{nome}}`) and "+ Link prenota" (`{{link}}`, deep link to booking); live push preview
  with the tokens filled in.
- **Schedulazione**: "Invia ora" or "Programma" (date + time, in the salon's time);
  "Ripeti ogni settimana" switch, which runs the campaign again at the same time for at
  most 4 runs ("Stop dopo 4 invii"). A send cap can also stop it earlier: it counts the
  recipients reached by the campaign in total, not per run.
- CTA "Invia ora" / "Programma invio". Name and message are required; a campaign with
  nobody reachable is refused rather than sent to no one.
- Sending is queued, never done inside the tap: recipients are worked out at the moment of
  the send (so a client who opted out in the meantime is left alone), a client is never
  written to twice in the same day, and when the run is over the owner gets an in-app
  notification "Campagna inviata". A campaign already sent can no longer be edited.

### 4.3 Agenda
- Header: "Agenda settimanale", "N operatori · N appuntamenti", notification bell (§5);
  under it the **day bar** (§7b) — previous/next-day arrows, the selected day's date,
  "Oggi" when the selected day is not today, and the six-day strip around it. It is the
  same control as the staff agenda (§3.1): the two agendas navigate time identically.
- One column per operator (initials, first name) over an hour rail with half-hour guides;
  card height ∝ duration, with the readable floor of §3.1 — a card too short for its
  services shows the client's name alone rather than a cut line, and a short appointment
  with free time after it grows into it until the services fit;
  the hour rules run under the hour labels too. Cards use the same colours as the
  staff agenda (§3.1): olive while active, stone once completed, outlined and faded with
  "No-show" when the client didn't come — a card turns black only while it is being
  dragged; cancelled appointments are not shown. Blocks and the stretches where an operator is not on
  shift look the same: one grey band across the column, label centered — the block's
  label or reason ("Pausa pranzo", "Ferie"…), "Riposo" over a whole day off. An empty
  column must not read as free. The operator strip scrolls together with the
  columns; when the columns don't all fit the screen, the lanes stretch to fill it or a
  round arrow over the strip signals — and scrolls to — the operators off-screen.
- Tap a free spot → manual booking (§4.4) with operator, day and quarter hour set; taps on
  blocks and on off-shift stretches do nothing. Floating buttons "Ferie e permessi" and "Nuova prenotazione".
- **Drag & drop**: long-press a card and drag it to another time and/or operator column;
  it snaps to 15 minutes and a banner follows the finger with the target ("Su Luca alle
  10:15", "Su Giulia alle 10:15 · non in turno" over an off-shift stretch, or "Fuori
  dalla giornata" outside the rail). It lands only where it fits (§6.3):
  on success "Appuntamento spostato" and the client is notified, otherwise "Slot non
  disponibile" and the card goes back. The floating buttons disappear while a card is
  being dragged.
- Tap a card → bottom sheet: client (initials in Olive Wood on black), time · duration ·
  operator, price, status (Confermato · In corso · Completato · No-show), services, hint to
  drag the card to move it, "Chiama" in gold (opens the phone dialer with the client's
  number; compact beside a wider **Modifica appuntamento**, full width on a completed
  appointment), and "Annulla appuntamento" (second tap confirms;
  cancelled by the salon, and the client is notified as in §3.3). Once the start time has
  passed — also after the appointment closed itself as completed — **"Non si è
  presentato"** appears (second tap confirms; the client is notified); on a no-show,
  **"Segna come completato"** corrects it. A completed or no-show appointment offers no
  "Modifica" or "Annulla".
  "Modifica" opens the manual booking sheet (§4.4) prefilled with client, operator, day
  and services; the appointment gives up its own place, so its time stays selectable, and
  saving **replaces** it in one step (cancelled by the salon): a save that fails leaves the
  agenda untouched.
- **Ferie e permessi** sheet: Operatore, Motivo (Permesso · Pausa · Ferie · Corso), Data,
  Dalle ore / Alle ore, "Mezza giornata" / "Giornata intera"; conflicting appointments are
  counted ("N appuntamenti in conflitto — Spostali o annullali dall'agenda prima di
  bloccare questa fascia") and block the save. CTA "Blocca <range>".

The tablet layout uses the full width for the operator columns (§7).

### 4.4 Prenotazione manuale
Bottom sheet "Nuova prenotazione" (owner) / "Nuovo appuntamento" (staff), "Annulla" and
"Salva" in the bar.
- **Cliente**: live search by name or phone; each result shows phone and visits. "Crea
  nuovo cliente" opens inline fields Nome, Cognome, Telefono (a client record without app
  account or email).
- **Operatore** (owner only): dropdown with every operator, the chosen one ticked.
- **Data** picker; **Servizi**: a multi-select dropdown twin of the operator one, a
  checkmark per chosen service, the field summing them up.
- When the chosen operator and services yield no time, the empty slot row says why:
  "Questo operatore non esegue i servizi scelti" or "L'operatore non è in turno in
  questo giorno" — an empty agenda column does not mean the operator is bookable.
- Editing (§4.3) shows the appointment's client as a fixed row: no search, no
  "Crea nuovo cliente".
- **Orario**: the whole day's free starts for the chosen services, in one horizontal row;
  the label shows the total duration. Opened from a tap on the agenda, the tapped time is
  selected as soon as the services fit there ("Ore 10:30 · scegli i servizi per
  confermare" until then; "Alle 10:30 non c'è posto per questi servizi: scegli un altro
  orario" if they don't).
- **"Invia SMS di conferma"** ("Il cliente non ha l'app") — default ON.
- CTA "Inserisci in agenda". The appointment is recorded as booked by phone.

### 4.5 Gestione · Servizi
- Sub-tabs: Servizi · Operatori · Notifiche · Orari.
- "Listino · N": flat list; row = name, "<durata> min · N operatori", price. Tap → edit.
- Service editor ("Nuovo servizio" / "Modifica servizio"): **Nome** (required),
  **Durata** stepper (5–240 min), **Prezzo (€)**, **Operatori abilitati** (checklist —
  drives which operators and services the wizard offers). Saving rewrites that list in both
  directions: an operator ticked gains the service, one unticked loses it. Services are
  never deleted, only deactivated, so past appointments keep their history.
  "+ Nuovo servizio" at the bottom.

### 4.6 Gestione · Operatori
- List: initials, name, "<ruolo> · N servizi", "Titolare" badge on the owner; "+ Nuovo
  operatore" at the bottom.
- Tap → the card expands (read-only): **Orario settimanale** (grouped days, "chiuso"),
  **Ferie e blocchi** (upcoming blocks grouped into periods), **Servizi assegnabili** (chips).
- **Nuovo operatore**: Nome e cognome (required), Ruolo (free text, hint "Barbiere, hair
  stylist…"), Email di accesso (required, valid, not already used), Telefono, **Giorni di
  lavoro** (seven chips), Dalle ore / Alle ore (the same range for every working day),
  **Servizi che esegue** (at least one). Saving also creates the operator's STAFF account
  with that email and a temporary password, which the server returns once, in the answer to
  the save: there is no invitation email, so the owner has to pass it on (§9).

### 4.7 Gestione · Notifiche
- **Promemoria automatici**: an ordered list ("1° promemoria", "2° promemoria"…). The 1st
  is "Un giorno prima" or "2 giorni prima"; the later ones "2 ore prima" or "4 ore prima";
  each value can be used once. "Aggiungi promemoria" adds an unused one; each one is
  removable. A rule is "N hours before the appointment", so the server accepts any whole
  number of hours from 1 to 168 even though the app offers those four.
- Reminders go out only to clients who left "Promemoria appuntamento" on (§2.5), once per
  appointment and per rule, and never for a booking made when that rule's moment had
  already gone by (a 2-hour reminder is not sent for a booking made 30 minutes ahead).
  There is no night-time window: a 2-hour reminder for a 09:00 appointment leaves at
  07:00 (§9).
- **Tipi di notifica** (switches): Conferma prenotazione and Annullamento decide whether
  the **client** is told when a booking is confirmed or the salon cancels; the operator is
  told either way. Operatore in ritardo and Promozioni nei giorni vuoti are saved but
  nothing sends them yet (§9) — default OFF for both.
- Push preview ("Ci vediamo domani alle 17:30 con Antonio. Rispondi per spostare.").

### 4.8 Gestione · Orari
One card per weekday: Aperto/Chiuso switch, Dalle ore / Alle ore (one range per day). The
salon's hours bound every operator's bookable time. "Salva orari".

### 4.9 Clienti (CRM)
- Search by name, phone or email; filters Tutti · Inattivi 60+; alphabetical sections by
  surname; rows show visits and last visit.
- Client sheet: initials, name, phone, "cliente dal <anno>"; tiles Visite, Spesa totale,
  No-show; **"Abitudini"** — "Operatore preferito" (the operator with the most completed
  visits, the latest on a tie) and "Torna in media ogni N giorni" (average gap between
  completed visits, rounded down), each shown only when there is enough history;
  "Storico appuntamenti" (date, services, operator, amount); in a bar pinned to the
  bottom (solid background, hairline and shadow above, over the scrolling history) "Chiama" (opens the phone
  dialer with the client's number) and "Nuova prenotazione", which opens the manual
  booking for that client. Staff see the same habits (§3.4).

### 4.10 Profilo
Photo (system picker, "Rimuovi foto"), name; **Account**: Email, Telefono; **Salone**: Nome,
Indirizzo (read-only); "Esci dall'account".

## 5. Notifications (every role)
- One page, opened from the bell (client Home, staff Agenda, owner Agenda); each account
  sees only its own notifications, oldest ones loaded as the page scrolls.
- Rows: title, body, date and time, a dot — full Olive Wood if unread when the page opened,
  faded if already read. Opening the page marks everything read and clears the bell's dot.
- What actually produces a notification, and for whom:

  | Event | Who is notified |
  |---|---|
  | Booking confirmed | the client (if the salon's "Conferma prenotazione" switch is on) and the operator |
  | Appointment moved | the client |
  | Cancelled by the salon | the client (if "Annullamento" is on) |
  | Cancelled by the client | the operator |
  | Reminder, N hours before (§4.7) | the client, if "Promemoria appuntamento" is on |
  | A slot frees up on a day the client is queued for (§6.4) | that one client, if "Slot liberi in lista d'attesa" is on |
  | Push campaign (§4.2) | every reachable client; the owner gets "Campagna inviata" when the run ends |
  | Appointment marked no-show | the client |

  Statuses advancing by themselves (in progress, completed) and correcting a no-show
  notify nobody. Notifications
  are kept indefinitely: nothing deletes them (§9).
- The text is written by the server, so both apps show the same wording.

## 6. Business rules

### 6.1 Slots
- A slot is a start time for the **total** duration of the chosen services, on a 30-minute
  grid, inside the operator's weekly hours intersected with the salon's opening hours,
  minus the operator's active appointments, blocks and holidays. The salon's hours always
  win: an operator's shift that sticks out of them is not bookable.
- The grid restarts at the beginning of each working range, so a split shift
  09:00–13:00 / 14:00–19:00 offers 14:00 and 14:30, not 13:30.
- Same-day bookings need 30 minutes of notice; past days offer nothing.
- **Every booking follows these rules, whoever makes it** — the client's wizard, the
  staff sheet and the owner's manual booking alike. Only moving an appointment already in
  the book is free-form (§6.3).
- "Qualsiasi operatore" = union of the slots of every operator able to perform **all** the
  chosen services; the server assigns the first of them free at that time, and the app
  shows who it landed on. If that operator is taken while the booking is being written, the
  server tries the next one before giving up. When nobody at all performs the chosen
  combination the answer says so ("Questo operatore non esegue i servizi scelti"), instead
  of pretending the time was taken.
- Service ↔ operator eligibility filters both the service list and the operators.
- Availability is re-checked while the booking is written, and the database itself refuses
  two overlapping appointments on the same operator: whoever confirms second is told the
  time has just gone ("slot taken while confirming").

### 6.2 Appointments
- Statuses: **Confermato** → **In corso** → **Completato**, or **Annullato** / **No-show**.
  Every booking is confirmed straight away: there is no approval step.
- **Statuses advance by themselves**: at its start time a confirmed appointment becomes
  "in corso", and at start + duration, if nobody handled it, "completato" — the server
  checks every minute, so nobody has to move statuses by hand. "Segna completato" still
  closes one early.
- **No-show is set by hand**: "Non si è presentato", only once the start time has passed,
  also on an appointment already closed as completed; the client gets a notification. A
  no-show marked by mistake goes back to completed ("Segna come completato"). Setting the
  status an appointment already has changes nothing, so no visit is ever counted twice.
  A cancelled appointment cannot be revived.
- A cancellation records who cancelled: the client (from the app) or the salon
  (staff/owner). A client can cancel an appointment up to **2 hours** before it starts;
  closer than that the app tells them to call the salon. "Modifica" replaces the old
  appointment with the new one (below), so the same window applies to changes. The salon has
  no limit.
- **Modifica = replace** (client wizard, owner and staff sheets alike): while editing,
  availability ignores the appointment being edited, so its own time stays selectable;
  saving cancels the old one and creates the new one in the same server transaction — if
  the new one doesn't fit, nothing changes.
- An appointment keeps the duration and total price it was booked with, even if the
  services change later.
- Channel: **App** (client wizard), **Telefono** (manual booking by staff/owner),
  **Walk-in** (shown as a flag on the staff agenda).
- "Segna completato" drives the client's visits, lifetime spend and last visit, the
  owner's KPIs and the "Inattivi 60+" segment.

### 6.3 Manual moves
Moving an appointment that already exists is free-form for the salon: it snaps to 15
minutes and only has to fit the target operator's working hours, holidays, blocks and other
appointments; the 30-minute grid and the 30-minute notice do not apply. The owner can move
any appointment, to another time or another operator's column; an operator can only move
the appointments in their own column, and cannot hand them to a colleague from the agenda
(that is what "Passa a <collega>" in §3.2 is for). A client who changes an appointment from
the app stays on the normal grid and keeps the same operator.

### 6.4 Waitlist
- A request is for **one day**, with a specific time ("slot taken while confirming") or
  any time ("Avvisami" on a fully booked day), for a specific operator or any, for the
  chosen services.
- One request per client, day and operator choice: joining again for the same day and the
  same operator updates the request that is already there — time, services and duration —
  and keeps the place in the queue. "Any operator" is its own choice, so a client can hold
  one request on Antonio and one on "qualsiasi" for the same day, in two different queues.
- Queue = requests for the same day and the same operator choice, in joining order; the
  position is live and shrinks when someone ahead leaves or is served.
- A slot frees up when an appointment is cancelled or moved away, or when a block is
  deleted. Requests for that day are then read in joining order and **one** person is
  notified: the first whose services fit — at the exact time asked for, if a time was
  asked for, otherwise at the first free time of the day. That request leaves the queue;
  the next slot to free up goes to the next person. The slot is not held: whoever books
  first gets it.
- A client who switched "Slot liberi in lista d'attesa" off is skipped and keeps their
  place in the queue; the announcement goes to the next person. A request made at the
  counter for a client with no app account is closed without a message, because there is
  nowhere to send it.
- Requests expire when their day has passed; a request that has been notified expires 24
  hours later. Nothing reopens an expired request, and nobody else is told if the person
  notified does not book.
- Changing an operator's hours or removing a holiday does not go looking for people to
  notify: only the three events above do (§9).

### 6.5 Clients and segments
Every count behind these comes from the appointments themselves — visits, spend, no-shows
and last visit are never stored on the client.
- **Tutti**: every client card, app account or not.
- **Inattivi 60+**: no completed visit in the last 60 days, or never one.
- **Fedeli**: at least 10 completed visits and one of them in the last 60 days.
- **No-show**: at least one no-show.
- **Top spesa**: the top 10% by lifetime spend among the clients who have ever spent
  anything — a moving definition, not a fixed amount.
- The CRM list (§4.9) offers Tutti and Inattivi 60+; campaigns (§4.2) offer Tutti,
  Inattivi 60gg and Top spesa. Fedeli and No-show exist in the data but no screen offers
  them yet (§9).
- A campaign reaches a client only if all three are true: marketing consent on the client
  card, an app account, and "Promozioni e novità" on in the profile (§2.5).

## 7. Tablets
- Both apps run on tablets (iPad, Android tablets) full screen in every orientation;
  phones are portrait only — the cut-off is a smallest width of 600 dp.
- Phone layouts are kept, centered: dark bands, bars and backgrounds span the full width,
  content stays within a 640 readable width; bottom sheets are capped at the same width.
- Exception: the owner's agenda (§4.3) uses the whole width for its operator columns.

## 7b. Design system

The two apps share one set of tokens (`core/designsystem` on Android, `Core/DesignSystem`
on iOS); a token changes in both, in the same commit.

- **Colour.** Every token clears 4.5:1 (WCAG 2.1 AA, normal text) on the surface the app
  actually puts it on — Stone `#EBEBEA` is the stricter of the two light surfaces, so the
  ink tones are measured against it. The accent has two roles that must not be swapped:
  `OliveWood` `#77654B` on light surfaces (text, icons, borders, and fills carrying Bone
  text), `OliveLight` `#BFA277` on the near-black bands — the gold of the logo's "MEN
  CARE", sampled from `design/logo-lockup.png`. Secondary text is `TextMuted` on light and
  `OnDarkMuted` on dark, never a faded Bone. Accent pills use the flat `OliveTint`, not the
  accent at an alpha, so their contrast does not depend on what is underneath.
- **Type.** Jost is thin-stemmed and Cormorant is a high-contrast serif, so nothing is set
  Regular: body from Medium, titles and labels from SemiBold, display serif Bold. No style
  goes under 11 sp, and only short uppercase labels go under 12 sp. Screens take a role
  from the scale — `Overline` for uppercase section labels, `Meta` for captions and grid
  hours — rather than resizing a role at the call site.
- **Day bar.** The agendas' day navigation is one component (`AgendaDayBar`), used by the
  owner (§4.3) and the operator (§3.1) alike: arrows, date, "Oggi" (shown only when the
  selected day is not today) and the day strip. Today always carries its gold dot, even
  when it is also the selected day — "where I am" and "where today is" stay two separate
  readings.
- **Radius.** Six values and no more (`Radii` on Android, `Radii` on iOS): 6 for
  micro-elements, 10 for small controls, 16 as the default for buttons, fields and cards,
  22 for large dark containers, 28 for full-width bands and sheets, and the pill. A screen
  picks the role, never the number.
- **Bottom navigation.** One black pill for all three roles (`BrandBottomBar`): a
  near-black bar with a thin gold hairline, floating over the light body, where the active
  item wears a translucent gold pill and gold label. It is the same glass as the tabs, so
  navigation and tabs read as one family instead of two unrelated controls.
- **Tabs.** `SegmentedTabs` on a dark band: translucent track with a light hairline, the
  active option in a gold pill. Every tabbed surface uses it — the client's
  Prossimi/Passati, the owner's Giorno/Settimana/Mese, the four Gestione sub-tabs.

## 8. Platform differences
- **Social login**: Android offers Google only; iOS offers Sign in with Apple + Google
  (App Store guideline 4.8).
- **Navigation**: Material bottom bar, top app bars and bottom sheets on Android;
  custom tab bar, `NavigationStack` and sheets with detents on iOS.
- **Push**: FCM on Android, APNs on iOS; both apps register their device token on the same
  endpoint. Neither transport is connected yet (§9) — notifications arrive in the app's
  notification page.

## 9. Open points
Decisions not taken yet, and things the screens show but the system does not do. Where a
rule was settled when the backend landed, it is in §9b, not here.

**Not decided**
- Payments: absent; prices are informative.
- Setting the **walk-in** channel: no form offers it (manual bookings are recorded as phone).
- Which service is "featured" (used by the Home when the client has no history) isn't
  editable.
- Editing an existing operator (hours, services, holidays), disabling one, and removing
  services or operators: no screen offers it, although the data allows it.
- The **Fedeli** and **No-show** segments (§6.5) exist in the data but no screen filters by
  them.

**Wired to nothing yet**
- **Delivery of the password-reset link** (§1.4): the link is created and works, but no
  email is sent — outside production it is written to the server log. The SMS and WhatsApp
  options send nothing, and the masked number shown is a fixed demo one.
- **Sign in with Google / Apple**: the server does not verify the provider's token yet, so
  social sign-in is refused in production ("L'accesso con Google non è ancora attivo") and
  never creates an account on its own.
- **Push delivery**: notifications are written and shown in the app, and logged on the
  server; FCM (Android) and APNs (iOS) delivery is not connected.
- **Operatore in ritardo** and **Promozioni nei giorni vuoti** (§4.7): the switches are
  saved, nothing reads them.
- The **"Invia SMS di conferma"** switch (§4.4) isn't saved with the booking.
- **"Proponi altro orario"** (§3.2) has no proposal flow behind it: the conflict is only
  marked as handled.
- Buttons with no behaviour yet: "Al calendario" / "Aggiungi al calendario" (client),
  "Modifica" in the client sheet, "+ aggiungi" in the operator card.
- A new operator's temporary password (§4.6) is returned once when the account is created:
  no invitation email, and nothing forces a change at first sign-in.

**Rough edges to settle**
- Reminders have no night-time window: a 2-hour reminder for a 09:00 appointment leaves at
  07:00.
- "Segna completato" can still close an appointment before it has happened (the no-show
  waits for the start time).
- Staff and owner can drag an appointment into the past; only the client's side refuses it.
- Notifications are never deleted or archived.
- The agenda rail in both apps is a fixed 09:00–20:00 window: opening hours set outside it
  (§4.8) do not appear in the staff and owner agendas.
- Waitlist requests are only re-scanned on a cancellation, a move or a deleted block
  (§6.4); a shortened appointment or a widened shift frees a slot nobody is told about.
- "Passa a <collega>" (§3.2) says nothing when the colleague is busy at that time: the
  conflict simply stays unresolved.

## 9b. Server-side rules (Phase 2)

Settled when the backend landed; `docs/API.md` is the endpoint contract and
`backend/migrations/0001_init.sql` the schema. The apps are being moved onto it one
repository at a time: a screen still served by the in-memory demo data behaves as it did in
Phase 1 until its repository is switched over, and only then follows the rules below.

- **Cancellation**: a client may cancel an appointment up to **2 hours** before it starts;
  closer than that the app tells them to call the salon. Since "Modifica" replaces the old
  appointment (`replacesAppointmentId`, one transaction), changes obey the same window.
  Staff and owner cancel at any time. No penalty is charged.
- **Statuses**: a scheduled job (every minute) moves confirmed appointments to in progress
  at their start and to completed at their end. By hand (`POST /appointments/:id/status`):
  NO_SHOW once the start has passed, also from COMPLETED — the client is notified
  (`BOOKING_NO_SHOW`) — and COMPLETED from NO_SHOW to correct it. Transitions are guarded
  and idempotent, so no visit is counted twice.
- **Client counters** (visits, lifetime spend, no-show count, last visit) are derived from
  the appointments, never stored: cancelling or correcting an appointment fixes the stats.
- **Slot generation, availability, next availability, queue positions, client stats, KPIs
  and campaign reach are computed by the server.** The apps display them.
- **Concurrency**: overlapping appointments for one operator are impossible — the database
  refuses them. A client confirming a slot someone else just took gets
  `SLOT_NO_LONGER_AVAILABLE`.
- **Waitlist**: one queue entry per client, day and operator choice. When a slot frees up
  (cancel, move, block removed) exactly one person is notified — the first in line whose
  services fit — honouring their "Slot liberi" switch; the slot is not held. Entries expire
  when their day passes, notified ones after 24 hours, and nothing reopens them.
- **Reminders**: one notification per appointment per reminder rule, only if the client's
  "Promemoria appuntamento" switch is on, and never for a booking made after the reminder
  time had already passed.
- **Campaigns**: recipients = marketing consent on the client sheet + a linked account with
  "Promozioni e novità" on. Sending is queued, never inline; the send cap is cumulative and
  the weekly repeat stops after 4 runs.
- **Economic data**: for the STAFF role the server does not even select amounts — the owner
  is the only role that receives money fields.
- **Scope**: a client only ever reads its own rows; an operator only its own agenda, its own
  blocks and its own holidays — asking for a colleague's is refused, not silently ignored;
  the owner sees the whole salon and is the only one who can move an appointment to another
  operator or reach the Gestione endpoints.
- **Time**: the salon works in `Europe/Rome`; instants are stored in UTC.
- **Sessions**: access token 15 minutes, refresh token 30 days, rotated at every use;
  reusing a revoked refresh token closes every session of that account, while a normal
  logout closes only that device. Changing or resetting the password closes all of them.
  Reset links last 30 minutes and work once.
- **Validation**: the rules live in `backend/src/lib/validation.ts` and are mirrored by both
  apps (phone normalized to E.164 with +39 as default, password at least 8 characters with a
  letter and a digit, note 200 characters, durations in 5-minute steps, prices in cents).

## 10. Domain model

The entities as the screens above create and read them, and as
`backend/migrations/0001_init.sql` stores them. Identifiers are UUIDs, money is always
integer cents, instants are stored in UTC and shown in the salon's time (`Europe/Rome`).
*Derived* values are computed from other data, never entered by hand.

| Entity | Fields | Notes |
|---|---|---|
| **User** (account) | id, role `CLIENT`/`STAFF`/`OWNER`, first name, last name, email (unique, login), phone, password hash, avatar (staff/owner), disabled at, created at | One login for every role. A CLIENT account points at a client card, a STAFF/OWNER account at an operator; never both. Social identities (Google; Apple on iOS) sign in an existing account, they never create one (§9). |
| **Client** | id, first name, last name, phone (unique, E.164), email (optional), user id (optional), created at ("cliente dal") | A client registered from the app has a User; one created from the manual booking has none. *Derived* from the appointments: visits, lifetime spend, no-show count, last visit, favourite operator, average days between visits. |
| **ClientNotificationPrefs** | user id, appointment reminder, waitlist alerts, marketing | Defaults ON/ON/OFF. Marketing = the client's half of the campaign opt-in (§6.5). |
| **RefreshToken** (session) | id, user id, token hash, expires at, revoked at, replaced by, user agent, created at | One row per signed-in device; rotation links each row to the next (§9b). |
| **PasswordResetToken** | user id, token hash, expires at (30 min), used at | Single use. |
| **DeviceToken** | id, user id, platform `ANDROID`/`IOS`, token (unique), last seen at | Registered at sign-in, removed at logout. |
| **Salon** | name, address, city, phone, opening hours | Single row. Opening hours: per weekday, one or more ranges (split shift); no range = closed. |
| **Operator** | id, display name, role label, bio, specialties, is owner, active | The account link lives on the User (`operator id`). Photo lives on the User too. |
| **OperatorWorkingHours** | operator id, weekday, start, end | Several ranges per day allowed (e.g. lunch break); none = day off. Always intersected with the salon's hours. |
| **Service** | id, name, description, duration (5–240 min), price (cents), featured, active | Flat list, no categories. Services are deactivated, never deleted. |
| **OperatorService** | operator id, service id | Eligibility (N:N). |
| **Appointment** | id, client id, operator id, start, end, duration, total price, status, channel `APP`/`PHONE`/`WALK_IN`, note for operator (≤ 200), cancelled by `CLIENT`/`SALON`, cancelled at, completed at, created by, created at | Status `CONFIRMED`/`IN_PROGRESS`/`COMPLETED`/`CANCELLED`/`NO_SHOW`. End = start + duration. Two active appointments of one operator can never overlap: the database refuses it. |
| **AppointmentService** | appointment id, service id, position, name, price, duration | Name, price and duration copied at booking time, so history survives a price change. |
| **TimeBlock** | id, operator id, reason `PERMESSO`/`PAUSA`/`FERIE`/`CORSO`, date, start, end, label (optional) | One day each, a time range inside it (§3.2). |
| **Holiday** | id, operator id, from date, to date (inclusive), label | Whole days off, its own entity; two holidays of one operator cannot overlap. A holiday empties the day for bookings and is left out of the occupancy calculation (§4.1). |
| **WaitlistEntry** | id, client id, date, time (optional = any time), operator id (optional = any), services, duration, total price, status `WAITING`/`NOTIFIED`/`EXPIRED`, created at, notified at, expires at | One WAITING entry per client, day and operator choice. *Derived*: queue position (§6.4). |
| **ReminderRule** | id, hours before (1–168, unique) | Salon-level; the app offers 24 or 48 h for the first and 2 or 4 h for the later ones. Order = hours before, there is no separate position. |
| **NotificationSettings** | booking confirmation, cancellation, late operator, empty-day promos | Salon-level switches (§4.7); the last two are not read by anything yet (§9). |
| **PushCampaign** | id, name, segment `TUTTI`/`INATTIVI_60`/`TOP_SPESA`, title, body (≤ 140, tokens `{{nome}}` `{{link}}`), scheduled at (none = send now), repeat weekly, send cap, status `DRAFT`/`SCHEDULED`/`SENT`, reach and segment size stored at sending | A sent campaign is read-only. |
| **CampaignSend** | campaign id, client id, sent at | One row per client actually written to: it is what caps the sends and stops a client hearing the same campaign twice in a day. |
| **Notification** | id, user id, kind, title, body, payload, created at, read at | The in-app list (§5); the text is written by the server. Kinds: `BOOKING_CONFIRMED`, `BOOKING_REMINDER`, `BOOKING_CANCELLED`, `BOOKING_RESCHEDULED`, `BOOKING_NO_SHOW`, `WAITLIST_SLOT`, `CAMPAIGN`, `GENERIC`. |
