# CLAUDE.it.md — Antonio De Vito · Men Care

> Traduzione italiana di `CLAUDE.md` per il team. Claude Code legge `CLAUDE.md` (inglese): in caso di divergenza fa fede l'originale inglese.

App di prenotazioni per barberia con una sola sede. Due app native con le stesse funzioni — Android (Kotlin) e iOS (SwiftUI) — e un unico backend in `backend/` (Fastify + TypeScript + PostgreSQL, dockerizzati) con cui parlano entrambe.

**Leggere `docs/FEATURES.it.md` prima di implementare qualsiasi cosa** — è la specifica funzionale delle app così come sono implementate: schermate, campi dei form e validazioni, regole di business, punti aperti (§9) e il modello di dominio da cui deriva lo schema del backend (§10). **La UI implementata è il riferimento definitivo**: quando la UI cambia, `docs/FEATURES.md` e `docs/FEATURES.it.md` cambiano con lei, nello stesso commit. `design/mockup.dc.html` (27 schermate) è il riferimento visivo originale da cui le app sono partite — dove diverge dalle app, valgono le app. Gli asset del brand stanno accanto ad esso in `design/` (favicon.png, logo-lockup.png, logo-mark.png, logo-mark-dark.png).

**Leggere `docs/API.md` prima di toccare qualsiasi cosa passi dalla rete** — è il contratto degli endpoint fra `backend/` e le due app. Se un'app ha bisogno di qualcosa che nel contratto non c'è, si aggiorna prima `docs/API.md`.

## Struttura del repository

```
android/    l'app Android — root Gradle, tutti i moduli stanno qui
backend/    l'API — Fastify + TypeScript su PostgreSQL, dockerizzata; schema in
            migrations/, una cartella per area in src/modules/ (vedi backend/README.md)
ios/        l'app SwiftUI — progetto XcodeGen (`xcodegen generate`), stesse funzioni
            di Android, stesso backend
design/     mockup.dc.html + asset del brand, condivisi dalle due app
docs/       FEATURES.md — specifica funzionale delle app; API.md — contratto degli endpoint
```

I comandi Gradle si lanciano da `android/`, non dalla radice del repo (`cd android && ./gradlew …`).
I comandi del backend si lanciano da `backend/` (`cd backend && npm run dev`).

## Fasi di consegna — IMPORTANTE

1. **Fase 1 (conclusa): le due app client, senza backend.** Prima Android, con l'app SwiftUI in `ios/` tenuta allineata come port: una modifica a una va portata anche sull'altra. Costruite su un layer dati fittizio locale (repository in-memory popolati con dati demo realistici coerenti col mockup: operatori Antonio/Luca/Giulia/Sara, catalogo servizi con durate/prezzi reali, appuntamenti di esempio). Obiettivo raggiunto: il titolare installa l'APK — o apre l'app iOS — e naviga ogni schermata dei tre ruoli.
2. **Fase 2 (attuale): il backend vero.** Vive in `backend/` (Fastify + TypeScript + PostgreSQL): schema in `backend/migrations/0001_init.sql`, contratto degli endpoint in `docs/API.md`, regole lato server nel §9b di `docs/FEATURES.it.md`. Le app stanno passando dai repository fittizi a quelli di rete, dietro le stesse interfacce; i fake restano come fixture di test. Tutto quello che le app calcolavano da sole (slot, disponibilità, posizioni in coda, statistiche, KPI) adesso è lavoro del server.

## Sintesi del prodotto

Tre ruoli dietro un unico login (redirect per ruolo dopo l'autenticazione):
- **Cliente**: home con prossimo appuntamento, primi slot liberi e riprenotazione in un tap; wizard di prenotazione in 4 step (operatore → servizi → data/orario → riepilogo); lista d'attesa — "Avvisami" su un giorno al completo, o l'orario perso durante la conferma — con posizione in coda aggiornata; appuntamenti (prossimi, passati, lista d'attesa) con modifica/annullamento; profilo con dati personali, orari del salone, cambio password e preferenze notifiche.
- **Operatore (staff)**: propria agenda giornaliera, ferie e permessi con risoluzione dei conflitti, dettaglio appuntamento (nota del cliente, chiamata, segna-completato), prenotazione manuale, lista clienti, profilo.
- **Titolare (admin)**: dashboard KPI (incassi, appuntamenti, no-show, scontrino medio, occupazione, servizi più venduti, clienti inattivi), agenda giornaliera multi-operatore con drag & drop, prenotazione manuale e ferie e permessi, servizi (durata, prezzo, abilitazione operatori), operatori (creazione con account STAFF, dettaglio in sola lettura), regole notifiche, orari di apertura, campagne push con segmenti, CRM clienti, profilo.
- **Tutti i ruoli**: pagina notifiche in-app dietro la campanella dell'intestazione.

Dettagli completi, regole di business ed entità di dominio: `docs/FEATURES.it.md`.

## App Android

### Stack
- Kotlin, **Jetpack Compose + Material 3**, single-activity, Compose Navigation (un nav graph per area di ruolo + graph auth).
- **MVVM con flusso dati unidirezionale**: ViewModel + `StateFlow`, data class di stato UI immutabili, sealed class/interface per gli eventi UI.
- **Hilt** per la DI. **Coroutines/Flow** ovunque; niente RxJava.
- kotlinx.serialization. Coil per le immagini.
- Networking: Retrofit + OkHttp verso `/v1` (`docs/API.md`), JSON con kotlinx.serialization. I repository di rete si stanno scrivendo ora; finché un repository non è stato sostituito resta il suo fake e la UI non cambia.
- minSdk 26, target ultimo SDK stabile. Version catalog (`libs.versions.toml`) per tutte le dipendenze.

### Struttura a moduli (Gradle multi-modulo fin dall'inizio — è il requisito "facilmente modificabile")

La root Gradle è `android/`; ogni percorso qui sotto è relativo a essa.

```
:app                      — entry point, cablaggio navigazione, routing per ruolo
:core:designsystem        — tema, tipografia, colori, componenti riusabili
:core:model               — modelli di dominio (Kotlin puro, senza dipendenze Android)
:core:data                — INTERFACCE repository + implementazioni fake (Fase 1)
                            + implementazioni di rete (Fase 2)
:core:common              — utility, result wrapper, dispatcher
:core:ui                  — schermate condivise da più aree (lista clienti +
                            scheda cliente, usate da operatore e titolare; pagina
                            notifiche, usata da tutti e tre)
:feature:auth             — splash, login, registrazione, recupero password
:feature:client           — home, wizard prenotazione, appuntamenti, profilo
:feature:staff            — agenda, ferie e permessi, dettaglio appuntamento,
                            prenotazione manuale, profilo
:feature:admin            — dashboard, agenda, prenotazione manuale, ferie e permessi,
                            servizi, operatori, impostazioni notifiche,
                            orari di apertura, campagne, profilo titolare
```
I moduli feature dipendono solo da `:core:*`, mai l'uno dall'altro. Tutto l'accesso ai dati passa dalle interfacce repository in `:core:data` — è lì che si innesta il backend: sostituire un fake con la sua implementazione di rete non deve toccare né un ViewModel né una schermata. Un ViewModel non deve mai toccare direttamente una sorgente dati.

### Design system (dal mockup — adattare il mockup iOS alle convenzioni Android/Material mantenendo l'identità del brand)
- Colori: sfondo `#FDFDFD`, superficie/secondario `#EBEBEA`, accento "Olive Wood" `#867357`, quasi-nero `#000006` / `#000004` per fasce scure e testo. Header scuri su corpi chiari: è la firma del layout.
- Tipografia: **Cormorant Garamond** (display/titoli, serif) + **Jost** (corpo/UI, sans). Font inclusi come risorse.
- Definire tutto come token del tema Material 3 in `:core:designsystem`; nessun colore/dimensione hardcoded nel codice delle feature.
- Niente "Accedi con Apple" su Android: la schermata di login offre solo Google. `SocialProvider.APPLE` resta nel contratto condiviso per l'app iOS.
- Lingua UI: **italiano** (`values/strings.xml` di default in italiano). Tutto il testo visibile all'utente in string resources — nessuna stringa hardcoded — così le lingue future sono banali.
- Sostituire i pattern iOS con gli equivalenti Android: back di sistema + top app bar, bottom navigation Material, bottom sheet per blocco personale e prenotazione manuale, date picker Material dove sensato (calendario custom per lo step di prenotazione, per mostrare la disponibilità degli slot).
- Tablet (iPad e tablet Android) a schermo intero in ogni orientamento; i telefoni restano in verticale. Bande scure, barre e sfondi vanno a tutta larghezza, il contenuto resta centrato entro la larghezza di lettura comune (640 — `readableWidth()` / `ReadableMaxWidth` nel design system). Solo l'agenda del titolare usa tutta la larghezza.

### Sicurezza (vale già dalla Fase 1)
- Token/credenziali solo in Jetpack Security (`EncryptedSharedPreferences`/DataStore + Android Keystore). Mai in SharedPreferences in chiaro, mai nei log.
- Nessun segreto/API key committato nel repo; iniezione via `local.properties`/BuildConfig.
- Solo HTTPS (`usesCleartextTraffic=false`), network security config dal primo giorno.
- R8/ProGuard attivi nelle build di release.
- Controlli di ruolo nella navigazione: dal grafo cliente non devono essere raggiungibili destinazioni staff/admin.
- Validare tutti gli input lato client (e poi lato server; mai fidarsi del client).

### Test e qualità
- Unit test per ViewModel e logica di business (JUnit, Turbine per i Flow), in particolare generazione slot e totali carrello.
- I repository fake fanno anche da fixture di test.
- ktlint o detekt fin dall'inizio; configurazione pronta per CI con warning trattati come errori.

## Backend

Vive in `backend/`, accanto ad `android/` e `ios/`. `backend/README.md` spiega come avviarlo;
`docs/API.md` è il contratto; il §9b di `docs/FEATURES.it.md` elenca le regole che applica.

- **Fastify 5 + TypeScript**, **PostgreSQL 16**, entrambi in Docker (`docker-compose.yml`: api + db + volumi). Per il lavoro di tutti i giorni bastano `npm run dev` e un Postgres locale.
- Schema in migrazioni SQL numerate (`backend/migrations/0001_init.sql`), applicate una volta sola da `npm run migrate`; `npm run seed` carica il salone dimostrativo. Nessun ORM scrive lo schema.
- Struttura: `src/config` (variabili d'ambiente, validate all'avvio), `src/db` (pool, transazioni, forme JSON), `src/lib` (errori, validazioni, fuso orario, motore degli slot, testi), `src/plugins` (autenticazione e ruoli, gestione errori), `src/services`, `src/modules/<area>`, `src/jobs`.
- Auth: JWT di accesso 15 minuti + refresh 30 giorni con rotazione e rilevamento del riuso; hashing **argon2id**; accesso Google/Apple (Apple arriva all'API solo da iOS); token di reset password monouso, scadenza 30 minuti.
- **RBAC su ogni route, e dentro la query**: il CLIENT vede solo le proprie righe, lo STAFF solo la propria agenda e nessun importo (per lo STAFF le colonne economiche non vengono proprio selezionate), l'OWNER tutto il salone.
- Validazione di request e response con zod (`fastify-type-provider-zod`); le regole condivise stanno in `src/lib/validation.ts` e sono le stesse che le due app ripetono nei form.
- Rate limiting (@fastify/rate-limit), helmet, CORS ristretto alle origini delle app.
- **La disponibilità si calcola lato server e la prenotazione la riverifica dentro la transazione**; in più un vincolo di esclusione su `appointments` rende impossibili due appuntamenti sovrapposti, così chi perde la corsa riceve `SLOT_NO_LONGER_AVAILABLE`.
- Tempo: ogni istante è `timestamptz` in UTC; giorno e ora "da muro" si convertono al confine, in `src/lib/time.ts`; il salone lavora in `Europe/Rome`. Il denaro è sempre in centesimi interi.
- I contatori del cliente (visite, spesa, no-show) sono una vista sugli appuntamenti, non colonne salvate.
- Push: `PUSH_PROVIDER=log` di default (la notifica in app viene scritta comunque); l'invio FCM/APNs si completa in `src/services/push.ts`. I lavori pianificati (promemoria, scansione della lista d'attesa, campagne) girano su pg-boss, così Postgres resta l'unico servizio stateful.
- API versionate sotto `/v1`. Entità: §10 di `docs/FEATURES.it.md`.
- Logging strutturato (pino); nessun dato personale nei log.
- `npm run typecheck` e `npm test` (vitest) devono passare prima che una modifica entri.

## Convenzioni

- Conventional Commits.
- Modifiche piccole e focalizzate, dimensione da PR; build sempre verde.
- Tutto ciò che identifica l'attività deve restare sostituibile in fase di build: colori, palette, font, nome dell'attività, logo e gli altri asset del brand sono configurazione, non valori sparsi nel codice.
- Quando mockup e convenzioni Android confliggono, seguire le convenzioni Android preservando colori/tipografia/carattere del brand.
- Ogni modifica va sia su Android **sia** su iOS, e su ogni versione per ruolo di una schermata condivisa, insieme a `docs/FEATURES.md` + `docs/FEATURES.it.md` (e a `docs/API.md` quando si muove il contratto), nello stesso commit.
- Punti aperti elencati in `docs/FEATURES.it.md` §9 (pagamenti, canale walk-in, pulsanti ancora senza comportamento…): chiedere all'utente prima di inventare policy; per i puri stati di errore UI, implementare default sensati. Le regole già decise dal backend stanno nel §9b e non sono aperte.
