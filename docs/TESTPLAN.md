# Men Care · Piano di test manuale (UI)

Copertura manuale end-to-end delle due app (Android e iOS) contro il backend reale.
Riferimenti: `docs/FEATURES.md` (comportamento atteso), `docs/API.md` (contratto).
Ogni caso vale per **entrambe** le piattaforme salvo nota; le differenze lecite sono in
FEATURES §8. Un comportamento elencato in FEATURES §9 ("Open points") **non è un bug**:
non aprire segnalazioni per quelli.

## 0. Preparazione

- Backend avviato e **seed appena ricaricato** (`cd backend && npm run reset`, oppure via
  Docker `docker compose exec api node dist/scripts/migrate.js -- --reset` + seed). Molti
  casi presuppongono i dati demo intatti: rieseguire il reset tra una sessione di test e
  l'altra.
- Account demo (password unica `mencare2026`):
  - CLIENT `marco.esposito@gmail.com`
  - STAFF `luca.ferrante@mencare.it` (anche giulia.marchetti@…, sara.coppola@…)
  - OWNER `antonio@mencare.it`
- Per i test di concorrenza (WIZ-16, WIZ-17, WL-*) servono **due sessioni**: due
  emulatori/dispositivi, oppure app + una seconda app con il titolare che occupa lo slot.
- Il link di reset password non viene inviato per email: si legge nel **log del server**.
- Orologio: il salone ragiona in `Europe/Rome`. Se il dispositivo è su un altro fuso,
  aspettarsi orari coerenti col salone, non col dispositivo.
- Emulatore Android: se l'app non raggiunge il backend, controllare il proxy globale
  (`adb shell settings get global http_proxy`; azzerare con `settings put global http_proxy :0`).

Convenzione: ogni caso è `ID — titolo`, poi passi → **atteso**. Spuntare a mano.

---

## 1. Autenticazione (AUTH)

- [ ] **AUTH-01 — Login corretto per ruolo**: login con ciascuno dei tre account →
  cliente atterra su Home, staff su Agenda, titolare su **Agenda** (non Dashboard).
- [ ] **AUTH-02 — Credenziali errate**: email valida + password sbagliata → "Email o
  password non corretti"; nessun indizio su quale dei due campi sia sbagliato.
- [ ] **AUTH-03 — Mostra/nascondi password**: il toggle alterna testo/pallini senza
  perdere il contenuto.
- [ ] **AUTH-04 — Social login**: "Continua con Google" (e Apple su iOS) → messaggio
  "L'accesso con Google non è ancora attivo" (rifiuto pulito, niente crash). Nessun
  account creato.
- [ ] **AUTH-05 — Backend spento**: fermare l'API e tentare il login → errore generico
  gestito, niente crash né spinner infinito. Riavviare l'API e riprovare: funziona senza
  riavviare l'app.
- [ ] **AUTH-06 — Logout e rientro**: logout da ogni ruolo → si torna al login; nessuna
  schermata bianca; login immediato con un **altro** ruolo funziona (niente stato residuo
  del ruolo precedente).
- [ ] **AUTH-07 — Sessione lunga**: lasciare l'app aperta/inattiva oltre 15 minuti (scadenza
  access token) e poi navigare → i dati si caricano ancora (refresh trasparente), nessun
  logout inatteso.

### Registrazione (solo cliente)

- [ ] **AUTH-10 — Errori nascosti fino al primo submit**: aprire la registrazione, lasciare
  tutto vuoto: nessun errore visibile. Tap su "Crea account" → compaiono gli errori, e da
  lì si aggiornano **live** mentre si digita.
- [ ] **AUTH-11 — Regole campo per campo**: nome/cognome obbligatori; telefono col prefisso
  +39 fisso, minimo 8 tra cifre e spazi; email malformata rifiutata; password < 8 rifiutata;
  meter Debole/Media/Sicura si muove; conferma diversa → errore, uguale → "Coincide";
  checkbox termini obbligatoria.
- [ ] **AUTH-12 — Email già registrata**: registrarsi con `marco.esposito@gmail.com` →
  "Questa email è già registrata".
- [ ] **AUTH-13 — Presa in carico scheda da banco**: dal titolare creare un cliente da
  prenotazione manuale (solo nome+telefono), poi registrarsi da app con quello stesso
  telefono → l'account nasce **agganciato alla scheda esistente** (lo storico visite del
  banco è visibile nel nuovo profilo).
- [ ] **AUTH-14 — Numero già collegato**: registrarsi con il telefono di Marco →
  "Questo numero è già collegato a un account".
- [ ] **AUTH-15 — Recupero password, risposta neutra**: inviare il link a un'email
  registrata e a una inesistente → **stessa** conferma "Link inviato" in entrambi i casi.
- [ ] **AUTH-16 — Link di reset**: prendere il link dal log del server, impostare la nuova
  password → tutte le sessioni chiuse (l'app loggata su un altro device torna al login);
  il link **riusato** non funziona; un link più vecchio di 30 minuti non funziona.

---

## 2. Cliente · Home (CLI)

- [ ] **CLI-01 — Prossimo appuntamento**: card con countdown, ora, giorno, durata,
  operatore, servizi. **Nessun prezzo** in nessun punto della Home.
- [ ] **CLI-02 — Appuntamento in corso**: quando l'ora di inizio è passata ma non la fine,
  la card resta "prossimo" (lo split è sull'ora di **fine**).
- [ ] **CLI-03 — Nessun appuntamento**: con un account nuovo → "Nessun appuntamento in
  programma", il resto della pagina regge (niente sezioni rotte).
- [ ] **CLI-04 — Primi slot liberi**: max 4 chip, max 2 per giorno, entro 7 giorni, tutti
  uniformi in larghezza; nessun nome operatore nel titolo (disponibilità globale).
- [ ] **CLI-05 — Chip → wizard prefilled**: tap su un chip → wizard sullo step Data con
  servizi, giorno e ora già scelti.
- [ ] **CLI-06 — Chip rubato**: occupare (dal titolare) l'orario di un chip, poi tapparlo →
  il giorno resta selezionato, **l'ora no**; il cliente sceglie un altro orario. Nessun
  errore mostrato.
- [ ] **CLI-07 — Riprenota in un tap**: apre il wizard sullo step Data con operatore e
  servizi dell'ultima visita; "Storico" porta alla tab Appuntamenti.
- [ ] **CLI-08 — Cliente senza storico**: account nuovo → i chip usano un servizio in
  evidenza; la sezione Riprenota non c'è.

---

## 3. Wizard di prenotazione (WIZ)

### Navigazione e step

- [ ] **WIZ-01 — Barra continua sale/scende**: su ogni step 1–3 la barra col totale appare
  solo a scelta valida e **sparisce** se la scelta viene tolta (deselezionare tutti i
  servizi, deselezionare lo slot).
- [ ] **WIZ-02 — Annulla → Appuntamenti**: "Annulla" da qualsiasi step porta alla tab
  **Appuntamenti** (non Home).
- [ ] **WIZ-03 — Reset a ogni apertura**: scegliere un operatore, annullare, riaprire da
  tab "Prenota" → nessuna selezione residua. Vale anche dopo una conferma andata a buon fine.
- [ ] **WIZ-04 — Back tra step**: indietro da Data → Servizi → Operatore mantiene le scelte
  fatte (finché non si cambia qualcosa che le invalida).

### Step 1 · Operatore

- [ ] **WIZ-05 — Operatore senza disponibilità**: un operatore pieno per 14 giorni appare
  attenuato ma resta selezionabile.
- [ ] **WIZ-06 — Cambio operatore ripulisce**: scegliere Luca + un servizio che Giulia non
  esegue, tornare indietro e scegliere Giulia → il servizio non eseguibile è stato tolto,
  giorno e ora azzerati.
- [ ] **WIZ-07 — Qualsiasi operatore**: mostra "Il doppio degli orari disponibili"; allo
  step Servizi offre l'intero listino attivo.

### Step 2 · Servizi

- [ ] **WIZ-08 — Lista piatta filtrata**: solo i servizi dell'operatore scelto; niente
  categorie; nome, durata, prezzo. La barra somma ("2 servizi · 75 min · € 22,00").
- [ ] **WIZ-09 — Toggle servizio azzera la data**: con giorno+ora scelti, tornare ai servizi
  e aggiungerne uno → giorno e ora azzerati (gli slot dipendono dalla durata totale).
- [ ] **WIZ-10 — Servizio disattivato invisibile**: disattivare un servizio dal titolare
  (togliendogli tutti gli operatori non basta: serve la disattivazione, se esposta; in
  alternativa verificare che un servizio senza operatori abilitati non compaia con un
  operatore specifico) → non è prenotabile, ma resta visibile nello **storico** di chi lo
  ha già fatto.

### Step 3 · Data e orario

- [ ] **WIZ-11 — Stati del calendario**: giorni passati e domenica (chiusura) sbiaditi e non
  tappabili; giorno pieno **bordato senza pallino**; giorno disponibile pieno con pallino.
  Freccia mese precedente disabilitata sul mese corrente.
- [ ] **WIZ-12 — Slot sulla durata totale**: con 1 servizio da 45' e poi 2 da 75', la griglia
  del medesimo giorno cambia (gli incastri stretti spariscono con la durata maggiore).
- [ ] **WIZ-13 — Preavviso 30 minuti**: oggi, la griglia non offre orari entro 30 minuti da
  adesso.
- [ ] **WIZ-14 — Turno spezzato**: per un operatore con 09–13/14–19 (Antonio o Luca), la
  griglia non offre 13:30: il primo pomeridiano è 14:00.
- [ ] **WIZ-15 — Giornata al completo → Avvisami**: riempire un giorno di un operatore
  (blocchi dal titolare) e selezionarlo → pagina "Giornata al completo" con illustrazione
  animata e "Avvisami" fisso in basso. Tap → "Ti avviseremo · N° in fila"; uscire e
  rientrare sul giorno → stato "già in lista" mantenuto; la richiesta compare in
  Appuntamenti.
- [ ] **WIZ-16 — Slot rubato in conferma**: due sessioni sullo stesso slot; la seconda che
  conferma → torna allo step 3 con "Questo orario è appena stato prenotato…", disponibilità
  aggiornata (lo slot non c'è più), card "Mettimi in lista" per **quell'ora esatta** →
  "N° in fila". Scegliere un altro orario chiude la card.
- [ ] **WIZ-17 — Rubato l'ultimo slot del giorno**: come WIZ-16 ma sull'ultimo slot → al
  rientro sullo step 3 il giorno mostra direttamente "Giornata al completo".
- [ ] **WIZ-18 — Qualsiasi + assegnazione**: prenotare con "Qualsiasi operatore" → in
  conferma compare l'operatore **realmente assegnato**; l'appuntamento in Appuntamenti
  riporta lo stesso nome.
- [ ] **WIZ-19 — Combinazione impossibile**: con "Qualsiasi", scegliere due servizi che
  nessun singolo operatore esegue entrambi → messaggio "Questo operatore non esegue i
  servizi scelti" (non "orario occupato").

### Step 4 · Riepilogo e conferma

- [ ] **WIZ-20 — Note**: contatore live, blocco a 200 caratteri (provare incolla lungo);
  niente suggerimenti/chip. La nota compare nel riepilogo della pagina di conferma e nel
  dettaglio visto dallo staff.
- [ ] **WIZ-21 — Doppio tap su Conferma**: tap rapido ripetuto → **un solo** appuntamento
  creato.
- [ ] **WIZ-22 — Conferma**: pagina scura centrata; recap con operatore, servizi, durata,
  totale, nota (solo se scritta); "Torna alla Home" → Home aggiornata con il nuovo
  appuntamento; notifica "prenotazione confermata" al cliente (se lo switch del salone è
  ON) e all'operatore.

### Modifica

- [ ] **WIZ-23 — Modifica prefilled**: "Modifica" su un appuntamento futuro → wizard su
  step 1 con operatore, servizi e nota precompilati; titolo "Modifica prenotazione".
- [ ] **WIZ-24 — Prima il nuovo, poi l'annullo**: completare una modifica → il vecchio
  risulta "Annullato dal cliente", il nuovo Confermato. Forzare il fallimento (rubare lo
  slot scelto dalla seconda sessione) → **l'originale resta intatto**.
- [ ] **WIZ-25 — Finestra 2 ore**: su un appuntamento che inizia tra meno di 2 ore, Annulla
  e Modifica vengono rifiutati con l'invito a chiamare il salone.

---

## 4. Cliente · Appuntamenti e Profilo (APT / PRO)

- [ ] **APT-01 — Split Prossimi/Passati**: contatori corretti; un appuntamento in corso sta
  nei Prossimi; appena finito (ora di fine passata) scivola nei Passati.
- [ ] **APT-02 — Annulla con dialog**: "Annullare l'appuntamento?" → conferma → sparisce dai
  Prossimi, nei Passati come "Annullato dal cliente"; l'operatore riceve la notifica.
- [ ] **APT-03 — Righe passate**: annullato dal salone vs dal cliente etichettati diversi;
  no-show marcato "No-show" con data attenuata; "Riprenota" su una riga passata apre il
  wizard prefilled.
- [ ] **APT-04 — Strip statistiche**: visite completate, operatore preferito, media giorni
  tra visite: coerenti con lo storico del seed; dopo un "Segna completato" dello staff i
  numeri si aggiornano.
- [ ] **APT-05 — Tab vuota**: account nuovo → copy + "Prenota ora"; "Riprenota l'ultimo"
  assente senza storico.

### Lista d'attesa (WL)

- [ ] **WL-01 — Riga in lista**: dopo WIZ-15/16 la sezione "Lista d'attesa" mostra giorno,
  ora o "qualsiasi orario", operatore o "Qualsiasi operatore", posizione; "Esci" toglie la
  riga e fa scalare la coda dell'altro cliente (verificare con due account in coda).
- [ ] **WL-02 — Una richiesta per giorno+operatore**: rifare "Avvisami" sullo stesso giorno
  e operatore con servizi diversi → la richiesta **si aggiorna**, la posizione non cambia.
  Stesso giorno ma "qualsiasi operatore" → **seconda** richiesta separata.
- [ ] **WL-03 — Slot liberato → uno solo avvisato**: due clienti in coda sullo stesso
  giorno; il titolare cancella un appuntamento di quel giorno → **solo il primo** riceve
  la notifica; la sua riga sparisce; il secondo resta in coda e passa 1°.
- [ ] **WL-04 — Preferenza OFF salta ma non elimina**: primo in coda con "Slot liberi in
  lista d'attesa" OFF → alla liberazione viene avvisato il **secondo**; il primo resta in
  coda con la sua posizione.
- [ ] **WL-05 — Scadenza**: una richiesta per un giorno passato non compare più; una
  notificata sparisce dalla lista.

### Profilo cliente

- [ ] **PRO-01 — Modifica dati**: Modifica → campi editabili → Salva → i dati restano dopo
  logout/login. Email in un formato invalido rifiutata.
- [ ] **PRO-02 — Orari del salone**: raggruppati per giorni consecutivi uguali, "chiuso"
  sulla domenica; cambiando gli orari dal titolare (§4.8) il profilo cliente li riflette.
- [ ] **PRO-03 — Cambia password**: attuale sbagliata → errore sul campo; nuova senza cifre
  o < 8 → rifiutata con meter; successo → l'**altro** device viene sloggato, questo resta
  dentro; nuovo login solo con la nuova password.
- [ ] **PRO-04 — Preferenze notifiche**: default ON/ON/OFF. "Promemoria appuntamento" OFF →
  nessun promemoria ricevuto (vedi NOT-03); "Promozioni e novità" ON è metà del consenso
  campagne (vedi OWN-CAM-02).

---

## 5. Staff / Operatore (STA)

- [ ] **STA-01 — Solo la propria agenda**: Luca vede solo i propri appuntamenti; il giorno
  vuoto mostra comunque il binario orario con "Nessun appuntamento · tocca un orario per
  prenotare".
- [ ] **STA-02 — Card e stati**: card proporzionali alla durata; olive=confermato,
  nero=in corso, stone=completato; flag "walk-in" dove previsto; i blocchi appaiono in
  stone con motivo/etichetta.
- [ ] **STA-03 — Tap su slot libero**: apre "Nuovo appuntamento" con giorno e quarto d'ora
  preimpostati; tap su un blocco → nessuna azione.
- [ ] **STA-04 — Ferie e permessi con conflitti**: creare un blocco sopra un appuntamento →
  conflitto elencato, salvataggio bloccato; "Passa a <collega>" sposta l'appuntamento al
  collega alla stessa ora; poi il blocco si salva e la fascia sparisce dagli slot cliente.
  Edge: collega **occupato** alla stessa ora → il conflitto resta (comportamento noto,
  FEATURES §9: nessun messaggio).
- [ ] **STA-05 — Dettaglio appuntamento**: nota del cliente visibile se scritta; storico
  visite **senza importi**; "Chiama" disabilitato per cliente senza telefono; "Annulla"
  → annullato dal salone → notifica al cliente solo se lo switch "Annullamento" è ON.
- [ ] **STA-06 — Segna completato idempotente**: doppio tap → **una** visita contata (le
  visite del cliente in CRM non raddoppiano). Completato → non torna indietro.
- [ ] **STA-07 — Prenotazione staff**: sheet con operatore fisso e **solo i propri**
  servizi; griglia da 30' e preavviso 30' come il cliente.
- [ ] **STA-08 — Niente cifre, mai**: in tutta l'area staff (agenda, dettaglio, clienti)
  nessun prezzo/incasso visibile.
- [ ] **STA-09 — Scope server**: qualunque tentativo indiretto di vedere dati di un collega
  (es. cliente aperto dalla lista mostra visite ma non agende altrui) resta nel proprio
  perimetro.

---

## 6. Titolare (OWN)

### Dashboard

- [ ] **OWN-DAS-01 — Periodi**: Giorno/Settimana/Mese cambiano tutti i numeri; un periodo
  senza attività mostra **zeri**, non dati inventati.
- [ ] **OWN-DAS-02 — Incassi = solo completati**: prenotare e completare un appuntamento
  oggi → Incassi del giorno crescono del prezzo; uno solo confermato non conta.
- [ ] **OWN-DAS-03 — Trend con finestra vuota**: se la finestra precedente è vuota il trend
  è 0, non ±∞.
- [ ] **OWN-DAS-04 — Occupazione**: barre ≤100%; un operatore in ferie non abbassa
  artificialmente la propria occupazione (le ferie escono dal denominatore).
- [ ] **OWN-DAS-05 — No-show %**: marcare un no-show → percentuale aggiornata a una
  decimale.

### Campagne push

- [ ] **OWN-CAM-01 — Reach coerente**: "N di M": N = consenso su scheda + account + switch
  "Promozioni" ON. Spegnendo lo switch dal profilo di Marco, N cala di 1.
- [ ] **OWN-CAM-02 — Zero raggiungibili**: campagna su segmento vuoto → rifiutata, non
  inviata a nessuno.
- [ ] **OWN-CAM-03 — Corpo e token**: 140 caratteri col contatore; `{{nome}}` e `{{link}}`
  riempiti nell'anteprima; nome e messaggio obbligatori.
- [ ] **OWN-CAM-04 — Invio**: "Invia ora" → i clienti raggiungibili ricevono la notifica,
  il titolare riceve "Campagna inviata"; la campagna inviata è **read-only**; lo stesso
  cliente non riceve due volte nello stesso giorno.
- [ ] **OWN-CAM-05 — Opt-out tra scheduling e invio**: programmare, poi spegnere
  "Promozioni" a un cliente prima dell'orario → quel cliente non riceve.

### Agenda

- [ ] **OWN-AGE-01 — Colonne**: una per operatore; contatore "N operatori · N appuntamenti"
  coerente col giorno.
- [ ] **OWN-AGE-02 — Drag & drop**: long-press e trascina; snap a 15'; banner col target;
  su collisione → "Slot non disponibile" e la card **torna dov'era**; su successo →
  "Appuntamento spostato" e il cliente riceve la notifica.
- [ ] **OWN-AGE-03 — Drag cross-operatore**: il titolare sposta su un'altra colonna; lo
  **staff** dalla propria agenda non può (solo orario, stessa colonna).
- [ ] **OWN-AGE-04 — Spostamento libero vs regole**: spostare un appuntamento esistente
  alle :45 funziona (free-form, 15'); una **nuova** prenotazione alle :45 non è offerta
  (griglia 30').
- [ ] **OWN-AGE-05 — Sheet appuntamento**: tap card → sheet con prezzo (solo qui: il
  titolare vede le cifre); "Annulla appuntamento" chiede il **secondo tap** di conferma.
- [ ] **OWN-AGE-06 — Tap su libero**: apre la prenotazione manuale con operatore, giorno e
  quarto d'ora impostati.

### Prenotazione manuale

- [ ] **OWN-MAN-01 — Ricerca cliente**: per nome e per telefono; risultato con visite;
  "Crea nuovo cliente" inline (nome, cognome, telefono) crea una scheda **senza account**.
- [ ] **OWN-MAN-02 — Orario tappato + servizi**: aperta dal tap in agenda → "Ore 10:30 ·
  scegli i servizi per confermare"; scegliendo servizi che non ci stanno → "Alle 10:30 non
  c'è posto per questi servizi: scegli un altro orario".
- [ ] **OWN-MAN-03 — Canale**: l'appuntamento creato risulta "telefono" (visibile dal flag
  sull'agenda staff: nessun "walk-in").
- [ ] **OWN-MAN-04 — Duplicato telefono**: creare un nuovo cliente con un telefono già in
  archivio → rifiutato o agganciato alla scheda esistente, mai due schede con lo stesso
  numero.

### Gestione

- [ ] **OWN-SVC-01 — Editor servizio**: durata a step di 5 (5–240); prezzo; salvare con
  nome vuoto → rifiutato. La checklist operatori **riscrive nei due sensi**: spuntare
  Giulia le fa comparire il servizio nel wizard; togliere la spunta glielo toglie.
- [ ] **OWN-SVC-02 — Prezzo cambiato, storico intatto**: cambiare il prezzo di un servizio →
  gli appuntamenti passati e futuri già presi mantengono il **vecchio** totale; i nuovi
  usano il nuovo.
- [ ] **OWN-OPE-01 — Nuovo operatore**: form completo (email valida non usata, almeno un
  servizio, giorni + fascia) → creato l'operatore **e** l'account STAFF; la password
  temporanea è mostrata **una sola volta**; login immediato con quella password funziona
  e apre l'area staff.
- [ ] **OWN-OPE-02 — Email duplicata**: nuovo operatore con l'email di Luca → rifiutato.
- [ ] **OWN-OPE-03 — Card espansa**: orario settimanale raggruppato, ferie/blocchi a
  periodi, chip servizi; tutto read-only (la modifica non esiste ancora: FEATURES §9).
- [ ] **OWN-NOT-01 — Promemoria**: valori univoci (il 1° 24/48h, i successivi 2/4h);
  "Aggiungi promemoria" offre solo i non usati; rimozione ok.
- [ ] **OWN-NOT-02 — Switch Conferma/Annullamento**: OFF → il cliente non riceve più la
  notifica di conferma/annullo; l'**operatore** la riceve comunque.
- [ ] **OWN-ORA-01 — Orari salone**: chiudere il martedì → il wizard cliente mostra i
  martedì non selezionabili; restringere l'orario (es. 10–18) taglia gli slot degli
  operatori anche se il loro turno è più largo (l'orario del salone vince).
- [ ] **OWN-CRM-01 — Clienti**: ricerca per nome/telefono/email; filtro Inattivi 60+
  coerente (nessuna visita completata negli ultimi 60 giorni); sezioni alfabetiche per
  cognome.
- [ ] **OWN-CRM-02 — Scheda cliente**: tile Visite/Spesa/No-show; storico **con importi**
  (solo qui); "Nuova prenotazione" apre la manuale su quel cliente.

---

## 7. Notifiche in-app (NOT)

- [ ] **NOT-01 — Campanella e lettura**: pallino con non-lette; aprendo la pagina tutto
  diventa letto e il pallino sparisce; il pallino della riga resta pieno per le non lette
  al momento dell'apertura; scroll carica le più vecchie.
- [ ] **NOT-02 — Matrice eventi** (provarli tutti):
  prenotazione → cliente (se switch ON) + operatore; spostamento → cliente; annullo salone
  → cliente (se switch ON); annullo cliente → operatore; slot liberato → un solo cliente
  in coda; campagna → clienti raggiungibili + "Campagna inviata" al titolare. Completato /
  in corso / no-show → **nessuna** notifica.
- [ ] **NOT-03 — Promemoria**: con una regola "2 ore prima", prenotare per tra ~2 ore →
  la notifica arriva una sola volta; prenotare per tra 30 minuti → il promemoria da 2 ore
  **non** arriva (momento già passato); cliente con "Promemoria" OFF → niente.
- [ ] **NOT-04 — Isolamento account**: ogni account vede solo le proprie notifiche.

---

## 8. Trasversali e sicurezza (SEC / EDGE)

- [ ] **SEC-01 — Matrice prezzi**: cliente vede prezzi **solo** nel wizard/riepilogo/
  conferma; staff **mai**; titolare ovunque previsto (dashboard, sheet agenda, CRM).
- [ ] **SEC-02 — Nessuna via alle aree altrui**: da un login cliente non esiste percorso UI
  verso schermate staff/admin (provare back, deep state, rotazioni).
- [ ] **SEC-03 — Logout pulito**: dopo il logout, back button non rientra nell'area
  autenticata.
- [ ] **EDGE-01 — Backend giù a metà sessione**: fermare l'API con l'app aperta → le azioni
  mostrano errore gestito; al riavvio dell'API l'app riprende senza restart.
- [ ] **EDGE-02 — Doppio device stesso account**: login sullo stesso account da due
  emulatori; azioni da uno visibili dall'altro dopo refresh; il cambio password da uno
  slogga l'altro.
- [ ] **EDGE-03 — Rotazione/background**: ruotare (tablet) o mandare in background l'app a
  metà wizard e al rientro: stato conservato, nessun crash. Su telefono l'app resta
  portrait.
- [ ] **EDGE-04 — Tablet**: contenuto centrato entro la larghezza leggibile, bande scure a
  tutta larghezza; l'agenda del titolare usa tutta la larghezza.
- [ ] **EDGE-05 — Testi lunghi**: cliente con nome molto lungo, 3+ servizi su una card,
  nota di 200 caratteri: niente overflow o testi tagliati senza ellissi.
- [ ] **EDGE-06 — Data a cavallo di mezzanotte**: con un appuntamento domani alle 00:30
  (creato dal titolare via drag), countdown e split Prossimi/Passati coerenti.
- [ ] **EDGE-07 — Doppio tap ovunque**: sui CTA critici (Conferma, Blocca, Inserisci in
  agenda, Invia campagna) il doppio tap non crea duplicati.

---

## 9. Comportamenti noti da NON segnalare (FEATURES §9)

- Reset password / SMS / WhatsApp: nessun invio reale (link nel log del server).
- Social login rifiutato in modo esplicito.
- Push FCM/APNs non collegati: le notifiche vivono solo nella pagina in-app.
- "Al calendario" / "Aggiungi al calendario", "Modifica" nel dettaglio staff e nella scheda
  cliente, "+ aggiungi" nella card operatore: senza comportamento.
- Promemoria senza finestra notturna (può "partire" alle 07:00).
- Completato/no-show possibili prima dell'orario; nessun automatismo per i dimenticati.
- Staff/titolare possono trascinare nel passato.
- Binario agende fisso 09:00–20:00 anche con orari salone più larghi.
- "Passa a collega" muto se il collega è occupato.
- Lista d'attesa non riscandagliata per turni allargati o appuntamenti accorciati.
- Password temporanea del nuovo operatore mostrata una volta, nessun cambio forzato.
