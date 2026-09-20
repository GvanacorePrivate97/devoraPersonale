# Azioni per ruolo — stato di implementazione (Phase 1, dati fake)

Documento di lavoro per brainstorming. Non è lo spec ufficiale (quello resta `docs/FEATURES.md`) — è
una fotografia di **cosa può fare ogni tipo di utente nell'app oggi** e **cosa manca ancora**,
verificata leggendo il codice reale (screen + ViewModel + repository fake), non solo il mockup.

Legenda:
- ✅ **Fatto** — azione collegata a una logica reale (anche se su dati fake in-memory, coerente con la Fase 1).
- ⚠️ **Parziale** — l'elemento UI c'è ma il comportamento è limitato, sbagliato o diverso dallo spec.
- ❌ **Mancante** — pulsante/azione non collegato a nulla (no-op) o funzionalità del tutto assente.

---

## 0. Autenticazione (comune a tutti i ruoli)

- ✅ Login email + password con validazione ed errori (credenziali vs generico).
- ✅ Toggle "mostra password".
- ✅ Login social Google (solo Android, niente Apple — corretto da spec).
- ✅ Registrazione: nome, cognome, telefono +39, email, password + conferma, validazione live, indicatore forza password, checkbox termini obbligatoria.
- ✅ Recupero password via email (invio richiesta + schermata "email inviata").
- ⚠️ Recupero password via **SMS** e via **WhatsApp**: i due pulsanti nella schermata esistono ma richiamano la stessa funzione dell'invio email — non esiste un vero invio SMS né un deep-link WhatsApp distinto.
- ⚠️ Scadenza 30 minuti del link di reset: non modellata (il fake repository ritorna sempre successo, senza token/scadenza reali — accettabile in Fase 1, da ricordare per il backend).
- ✅ Logout (presente in Profilo cliente, "Altro" staff, "Sedi" admin).
- ⚠️ **Cambio password da loggato** — *aggiunto lato Cliente* (sezione "Gestione profilo" nel Profilo → form "Cambia password" con password attuale, nuova password con indicatore di sicurezza, conferma). Resta assente per Staff e Titolare, che non hanno ancora una sezione impostazioni dove metterlo.

**Da decidere:** cosa deve fare realmente il canale SMS/WhatsApp nel recupero password quando arriverà il backend?

---

## 1. Cliente

### Home
- ✅ Saluto con data, contatore visite a vita, badge notifiche non lette (reale).
- ✅ Card "prossimo appuntamento" con countdown, operatore, servizi, durata, prezzo totale.
- ❌ **"Aggiungi al calendario"** dal prossimo appuntamento — riga non cliccabile, nessuna integrazione con il calendario di sistema.
- ✅ CTA "Prenota ora" e "Lista d'attesa" → aprono il wizard di prenotazione.
- ✅ "Riprenota in un tap" → precompila il wizard con l'ultima prenotazione.
- ✅ Carosello "In evidenza" (servizi in evidenza).

### Prenotazione (wizard a 4 step)
- ✅ Step Operatore: "Qualsiasi operatore", card con bio/specialità/prossima disponibilità, operatori non disponibili in grigio.
- ✅ Step Servizi: catalogo filtrato per operatore scelto, raggruppato per categoria, multi-selezione, carrello con conteggio/durata/prezzo.
- ✅ Step Data e orario: calendario mensile, giorni non selezionabili calcolati sul serio (chiusure, giorni pieni), griglia slot sulla durata totale.
- ✅ Step Riepilogo: recap completo, note per l'operatore (max 200 caratteri) con chip rapide, **prenotazione ricorrente** (2/3/4 settimane) che genera davvero le istanze future.
- ✅ Gestione "slot preso nel frattempo": torna allo step data/orario e ricarica gli slot.
- ✅ Conferma: recap, banner serie ricorrente.
- ❌ **"Aggiungi al calendario"** dalla schermata di conferma — pulsante presente ma vuoto (no-op), stesso gap della Home.
- ❌ **Nessuna scelta della sede nel wizard** — la prenotazione usa sempre la "sede preferita" del profilo (o la prima sede se non impostata); per prenotare in una sede diversa bisogna prima andare in Profilo e cambiare la sede preferita, cosa che la cambia anche per il futuro. Con "3 sedi" nel business, non poter scegliere la sede al volo per una singola prenotazione è un limite reale.

### Appuntamenti
- ✅ Tab Prossimi/Passati con contatori, badge di stato (Confermato/Serie/In attesa).
- ✅ Annulla appuntamento (con conferma) → attribuzione salvata correttamente.
- ⚠️ **"Modifica" su un appuntamento futuro**: non modifica quello esistente, apre il wizard come **nuova** prenotazione precompilata con operatore/servizi — l'appuntamento originale resta invariato (nessun vero editing in-place).
- ✅ Lista d'attesa: posizione in coda, uscita dalla coda ("Esci").
- ✅ Storico passati con "Riprenota", stato di cancellazione attribuito.
- ✅ Statistiche cliente (visite totali, spesa anno corrente, giorni medi tra visite).
- ❌ **Notifica automatica quando si libera uno slot in lista d'attesa** — l'iscrizione/uscita funziona, ma nessuna logica promuove o avvisa il primo in coda quando un altro cliente cancella (vedi anche sezione trasversale).

### Profilo
- ✅ Modifica dati (nome, email, telefono, sede preferita).
- ✅ Preferenze notifiche (promemoria, lista d'attesa, marketing) con i default corretti da spec.
- ✅ Logout.

**Da decidere:** politica di cancellazione (scadenza/penale) — non c'è nel mockup né nel codice, resta un gap noto (spec §6).

---

## 2. Operatore (Staff)

### Agenda giornaliera
- ✅ Solo agenda propria, selettore giorno, riepilogo (n. appuntamenti, tempo occupato, % occupazione reale).
- ✅ Timeline con stato "in corso", flag walk-in, blocchi non prenotabili.
- ✅ FAB "Blocco personale".
- ✅ **FAB "Nuovo appuntamento"** *(aggiunto — non era nel mockup)*: lo staff può inserire da sé una prenotazione telefonica/walk-in sulla propria agenda (cliente, servizi, data/orario, SMS), senza passare dal titolare. A differenza della versione admin non c'è selettore operatore (è sempre "sé stesso") e i servizi mostrati sono solo quelli che l'operatore esegue davvero.

### Blocco personale
- ✅ Chip motivo (Permesso/Pausa/Ferie/Corso), scorciatoie "Mezza giornata"/"Giornata intera".
- ✅ **Rilevamento conflitti** con appuntamenti esistenti, blocco del salvataggio finché non risolti.
- ✅ Conflitto → **"Passa ad un collega"**: riassegna davvero l'appuntamento (chiamata reale a `reschedule`).
- ⚠️ Conflitto → **"Proponi altro orario"**: è uno stub dichiarato — segna il conflitto come "gestito" localmente, ma non manda nessuna proposta reale al cliente (nel codice è commentato esplicitamente come rimandato alla Fase 2/push).
- ✅ Toggle "Nascondi gli slot ai clienti" (default ON), nota per il titolare.

### Dettaglio appuntamento
- ✅ Intestazione cliente, riepilogo orario/importo/servizi.
- ✅ Note interne "solo staff" (allergie, preferenze) — **visualizzate**, ma nessuna schermata per **scriverle/modificarle** dallo staff (la funzione nel repository esiste già, manca solo l'UI).
- ✅ Storico visite cliente.
- ✅ **"Segna completato"** → aggiorna davvero contatore visite e incasso del cliente.
- ❌ **"Chiama"** cliente — pulsante presente ma vuoto, nessuna apertura del dialer.
- ❌ **"Modifica"** in alto — invece di aprire una modifica, torna semplicemente indietro (etichetta fuorviante).
- ❌ **Nessuna azione di cancellazione lato staff** — l'unica azione sull'appuntamento è "Segna completato"; lo staff non può annullare/rifiutare un appuntamento dal proprio lato (solo il cliente può farlo dalla sua app).

### Sezione "Altro"
- ❌ **Contenuto non definito** — placeholder "Sezione in arrivo" + solo il logout. Gap esplicito già segnalato nello spec, ancora aperto: cosa deve contenere?

### Trasversale staff/admin
- ❌ **Nessuna azione esplicita "segna no-show"** — lo stato esiste nel modello dati ma non c'è nessun pulsante (né per lo staff né per il titolare) che lo imposti; oggi compare solo nei dati demo precaricati.

**Da decidere per il brainstorming:** cosa deve contenere "Altro"; come deve funzionare davvero "Proponi altro orario"; chi marca il no-show e quando (fine giornata? manuale sull'appuntamento?).

---

## 3. Titolare (Admin)

### Dashboard
- ✅ Selettore sede, tutti i KPI ricalcolati per sede (incassi + trend, occupazione %, no-show %, scontrino medio — calcolo reale, con numeri di base "cosmetici" per sembrare realistici in demo).
- ✅ Occupazione per operatore, servizi più venduti.
- ✅ Widget "Inattivi da 60+ giorni" → CTA verso il composer campagne.

### Campagne push
- ✅ Segmenti (Inattivi 60gg / Tutti / Top spesa) con reach live.
- ✅ Composer messaggio (140 caratteri, token `{{nome}}`/`{{link}}`, anteprima live).
- ❌ **Campo "nome campagna" non esiste nell'interfaccia** — il ViewModel lo richiede per salvare, ma non c'è nessun campo per digitarlo: di fatto una campagna nuova non è mai salvabile da zero.
- ❌ **Selettore data/ora di invio programmato** — sono etichette fisse non cliccabili ("Lun 8 set", "10:30"); l'invio "programmato" usa sempre domani come data fissa in codice.
- ❌ **Invio reale push/SMS** — l'azione "invia" salva solo il record della campagna, non manda nessuna notifica reale a nessun dispositivo (previsto per la Fase 2 con FCM, ma va tenuto presente).

### Agenda settimanale
- ✅ Calendario multi-operatore, blocchi non prenotabili in griglia.
- ✅ Tap su slot vuoto → apre prenotazione manuale.
- ⚠️ **"Drag & drop"**: funziona (sposta davvero l'appuntamento), ma l'interazione reale è "tocca per selezionare, tocca la cella di destinazione per spostare", non un vero trascinamento col dito come da mockup.
- ❌ **Nessuna cancellazione lato titolare** — dall'agenda settimanale si può solo spostare un appuntamento, non annullarlo (es. cliente chiama per disdire: il titolare non ha un'azione diretta per farlo).

### Prenotazione manuale
- ✅ Canale Telefonica/Walk-in, ricerca cliente live, creazione cliente al volo, operatore/data/servizi/slot tutti reali (appena corretto il bug della preselezione operatore).
- ❌ **Toggle "Invia SMS di conferma"** — visivamente presente e attivabile, ma non ha nessun effetto: non viene passato da nessuna parte né usato per inviare un vero SMS.

### Gestione servizi
- ✅ Lista per categoria, editor completo (durata, prezzo, buffer, operatori abilitati, flag "richiede conferma manuale"), creazione nuovo servizio.
- ❌ **Gestione delle categorie stesse** — "Capelli", "Barba" ecc. sono fisse (precaricate nei dati demo); l'editor di un servizio permette solo di *scegliere* una categoria esistente, non di crearne, rinominarne o eliminarne una nuova.
- ❌ **Nessuna azione "elimina servizio"** — si può solo creare/modificare, non rimuovere un servizio dal catalogo.

### Operatori e orari
- ✅ Lista operatori con riepilogo orari settimanali, servizi assegnabili.
- ❌ **"+ aggiungi" per le ferie/blocchi** e **"+" per aggiungere un nuovo operatore** — icone presenti ma non cliccabili, mentre la logica per crearli esiste già nel repository (manca solo il collegamento).
- ❌ **Creare un nuovo operatore non crea comunque un account con cui accedere** — anche collegando quell'icona "+", oggi non esiste alcun collegamento tra il profilo "Operatore" (nome, orari, servizi) e uno "User" che possa fare login: non c'è nessun flusso di invito/creazione account per un nuovo barbiere assunto. Chi lo farebbe accedere all'app il primo giorno?
- ❌ **Chiusure valide per tutta la sede** (es. Natale, ferie collettive) — esistono solo ferie/blocchi per singolo operatore; per chiudere l'intera sede in una data bisognerebbe aggiungere manualmente lo stesso blocco a ogni operatore uno per uno.

### Impostazioni notifiche
- ✅ Promemoria multi-livello configurabili, tutti i toggle (conferma, cancellazione, "operatore in ritardo", "promo nei giorni vuoti") collegati davvero.

### CRM Clienti
- ✅ Ricerca, filtri per segmento, badge (Fedele/Inattiva/Ora in poltrona), scheda cliente con statistiche, note interne, storico.
- ❌ **"Messaggio"** dalla scheda cliente — pulsante vuoto, nessuna funzione di messaggistica.
- ❌ **"Modifica"** in alto sulla scheda cliente — torna solo indietro, nessun form di modifica dati cliente.
- ⚠️ **"Nuova prenotazione"** dalla scheda cliente — apre l'agenda settimanale ma **non porta con sé il cliente selezionato**: bisogna ricercarlo di nuovo nel foglio di prenotazione manuale.

### Prenotazioni in attesa di conferma
- ❌ **Nessuna azione da nessuna parte** — i servizi con "richiede conferma manuale" creano appuntamenti in stato "in attesa" (`PENDING`), il cliente vede il badge "in attesa" nella sua app, ma **non esiste alcuna schermata, per lo staff o per il titolare, che li elenchi e permetta di accettarli o rifiutarli**. Oggi una prenotazione pending resta pending per sempre: è forse il buco più concreto trovato, perché lo stato è già modellato in tutto il sistema ma manca l'azione che lo fa avanzare.

### Lista d'attesa (vista salone)
- ❌ **Invisibile a staff e titolare** — il cliente vede la propria posizione in coda, ma nessuna schermata admin/staff mostra chi è in lista d'attesa per un dato giorno/operatore. Se si libera un posto, oggi nessuno in salone saprebbe chi chiamare.

### Sezione "Sedi"
- ❌ **Sola lettura** — elenco sedi + logout, nessuna creazione/modifica sede. Gap esplicito già segnalato nello spec, ancora aperto.

**Da decidere per il brainstorming:** che aspetto deve avere davvero "Sedi" (multi-sede è nel business ma non c'è UI di gestione); come deve funzionare l'invio SMS/push reale; se il drag&drop reale è prioritario o se il tap-to-move attuale va bene anche per la versione finale.

---

## 4. Gap trasversali — elementi presenti ma "finti" (bottoni fantasma)

Cose che l'utente VEDE e può toccare, ma che non fanno quello che promettono:

1. **Integrazione calendario di sistema** ("Aggiungi al calendario") — presente in UI in 2 punti lato cliente, entrambi non collegati a nulla.
2. **Promozione automatica della lista d'attesa** — iscriversi/uscire funziona, ma nessuno "sale in fila" né viene avvisato quando un altro cliente libera lo slot.
3. **Invio reale di notifiche push e SMS** — sia le campagne marketing sia l'SMS di conferma prenotazione manuale sono solo "stato salvato", zero invio reale (previsto per il backend/Fase 2, ma da tenere a mente già ora per capire cosa serve all'infrastruttura).
4. **Pulsanti "fantasma"** (presenti ma senza effetto): "Chiama" (staff), "Messaggio" (CRM), "Modifica" su due schermate di dettaglio (staff e CRM) che in realtà tornano solo indietro.
5. **Politica di cancellazione** (scadenza/penale) — non decisa, il cliente può sempre cancellare senza limiti.
6. **Pagamenti/checkout** — assenti per scelta (i prezzi sono solo informativi), da confermare se resta così anche in produzione.

---

## 5. Buchi strutturali — azioni che mancano del tutto (non solo il pulsante: manca la logica sotto)

Questa è la parte più utile per il brainstorming: non "bottone rotto", ma **flusso che non esiste da nessuna parte**,
né in UI né nel repository. Verificato leggendo `BookingRepository`, `CatalogRepository`, `AuthRepository` e ogni
schermata staff/admin.

1. ❌ **Nessuno può mai confermare o rifiutare una prenotazione "in attesa"**. I servizi con "richiede conferma manuale" creano appuntamenti `PENDING` (previsto da spec), il cliente vede il badge "in attesa" — ma non esiste un `approve`/`reject` né in `BookingRepository` né in nessuna schermata staff/admin. Oggi un `PENDING` non diventa mai `CONFERMATO`. **Probabilmente il buco più concreto della lista**, perché lo stato è già modellato ovunque tranne che nell'azione che lo fa avanzare.
2. ❌ **Staff e titolare non possono mai annullare un appuntamento**. Solo il cliente ha un'azione di cancellazione (dalla sua app). Se un cliente chiama per disdire, chi risponde al telefono non ha modo di farlo nell'app.
3. ❌ **Creare un "Operatore" non crea un account con cui accedere.** Il profilo Operatore (nome, orari, servizi) e lo User che fa login sono due cose distinte e nulla le collega: non c'è un flusso di invito/creazione account per un nuovo barbiere assunto, né un modo di promuovere uno User esistente a STAFF/OWNER. La registrazione (schermo 02b) crea solo clienti.
4. ❌ **Nessuna chiusura "tutta la sede"** — ferie/blocchi sono solo per singolo operatore; per chiudere l'intero salone (es. Natale) bisognerebbe ripetere lo stesso blocco su ogni operatore uno per uno. Il modello `Venue` ha solo giorni di chiusura settimanali ricorrenti (es. sempre domenica), non chiusure straordinarie datate.
5. ❌ **Le categorie di servizio sono fisse** — "Capelli", "Barba" ecc. sono precaricate nei dati demo; l'editor di un servizio permette solo di scegliere una categoria esistente, non crearne/rinominarne/eliminarne una.
6. ❌ **Nessuna vista salone della lista d'attesa** — solo il cliente vede la propria posizione; staff/titolare non hanno alcuna schermata con "chi sta aspettando cosa", quindi non saprebbero chi richiamare se si libera un posto.
7. ⚠️ **Cambio password da loggato** — *risolto lato Cliente* (Profilo → "Gestione profilo" → "Cambia password"), ma resta assente per Staff e Titolare: nessuno dei due ha oggi una sezione impostazioni dove aggiungerlo.
8. ❌ **Scelta della sede nel wizard di prenotazione** — è sempre la sede preferita del profilo; niente selezione al volo per una singola prenotazione, nonostante il business abbia 3 sedi.
9. ❌ **Nessuna funzione di recensione/valutazione** — un cliente non può valutare la visita completata; assente sia dal mockup che dal codice (coerente, ma vale la pena decidere se introdurla).

### Spunti aggiuntivi da discutere (non verificati nel codice, solo idee per il brainstorming)
- Prenotare per conto di qualcun altro (es. un genitore prenota per il figlio) — il cliente ha un solo profilo, non "profili collegati".
- Programma fedeltà/voucher oltre al semplice conteggio visite (oggi le visite si contano ma non si "spendono" da nessuna parte).
- Esportazione dati/cancellazione account (rilevanza GDPR) lato cliente.
- Reportistica/esportazione incassi per il titolare (oggi la dashboard si vede solo a schermo, non si esporta).
- Un modo per il cliente di segnalare un problema/reclamo direttamente in app.

---

*Generato leggendo `docs/FEATURES.md` insieme al codice sorgente in `android/feature/*`, `android/core/data/src/main/kotlin/.../repository/` e `.../fake/` — utile come base di brainstorming, ma va riverificato quando il codice cambia.*
