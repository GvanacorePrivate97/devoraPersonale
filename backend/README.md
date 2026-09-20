# Men Care · API

Backend di Fase 2: Fastify + TypeScript su PostgreSQL. Serve le stesse funzioni
alle due app (Android e iOS), che fino a ieri lavoravano su dati finti.

## Avvio rapido (senza Docker)

Servono Node 22+ e un PostgreSQL 16+ in ascolto.

```bash
cp .env.example .env          # controlla DATABASE_URL e i due segreti JWT
npm install
npm run migrate               # crea lo schema
npm run seed                  # dati dimostrativi (salone, listino, agenda)
npm run dev                   # http://localhost:3000
```

Utile durante lo sviluppo:

```bash
npm run reset                 # schema da zero + dati demo
npm run typecheck
npm test
```

## Con Docker

```bash
JWT_ACCESS_SECRET=... JWT_REFRESH_SECRET=... docker compose up --build
docker compose exec api node dist/scripts/migrate.js
docker compose exec api node dist/scripts/seed.js
```

## Account dimostrativi

Stessa password per tutti: `mencare2026`.

| Ruolo | Email |
|---|---|
| Cliente | `marco.esposito@gmail.com` |
| Operatore | `luca.ferrante@mencare.it` |
| Titolare | `antonio@mencare.it` |

## Come è fatto

```
migrations/     SQL numerato, applicato una volta sola (0001_init.sql è lo schema)
scripts/        migrate.ts, seed.ts
src/config/     variabili d'ambiente, validate all'avvio
src/db/         pool, transazioni, forme JSON verso le app
src/lib/        errori, validazioni condivise, fuso orario, motore degli slot, testi
src/plugins/    autenticazione e ruoli, gestione errori
src/services/   disponibilità, notifiche, invio push
src/modules/    una cartella per area: auth, catalog, booking, blocks, crm, admin, notifications
src/jobs/       lavori pianificati (promemoria, lista d'attesa, campagne)
```

Regole che vale la pena conoscere prima di metterci mano:

- **Il fuso del salone è Europe/Rome.** Nel database ogni istante è UTC
  (`timestamptz`); date e orari diventano orario da muro solo al confine, in
  `src/lib/time.ts`. Gli orari di lavoro sono `time`, non istanti.
- **Il denaro è in centesimi**, sempre interi.
- **Due prenotazioni non possono sovrapporsi**: lo impedisce un vincolo di
  esclusione su `appointments`, non il codice applicativo. È la rete di
  sicurezza contro due clienti che confermano lo stesso orario insieme.
- **I contatori del cliente non si salvano**: visite, spesa e no-show sono la
  vista `client_stats`, calcolata dagli appuntamenti. Così "segna completato"
  può essere richiamato senza contare due volte.
- **Le validazioni stanno in `src/lib/validation.ts`** e sono le stesse che le
  due app ripetono nei form. Cambiarle qui significa cambiarle anche lì.
- **I dati economici sono del titolare**: nelle risposte al ruolo STAFF gli
  importi non vengono proprio selezionati, non solo nascosti.

## Errori

Tutte le risposte di errore hanno la stessa forma, e le app leggono `code`:

```json
{ "error": { "code": "SLOT_NO_LONGER_AVAILABLE", "message": "Questo orario è appena stato preso" } }
```

`VALIDATION` porta anche `field`, che è il campo del form da illuminare.

## Notifiche push

`PUSH_PROVIDER=log` (predefinito) scrive nel log e crea comunque la notifica in
app. Per passare a Firebase: `PUSH_PROVIDER=fcm`, credenziali del service
account in `FCM_SERVICE_ACCOUNT_JSON` e invio da completare in
`src/services/push.ts`. Nient'altro nel progetto cambia.
