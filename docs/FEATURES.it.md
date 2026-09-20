# Antonio De Vito — Men Care · Specifica funzionale

> Traduzione italiana di `FEATURES.md` per il team: in caso di divergenza fa fede l'originale inglese.

**Fonte di verità: le app implementate.** Questo documento descrive cosa fanno oggi l'app
Android (`android/`) e l'app iOS (`ios/`) e — dal §9b in poi — le regole che il backend in
`backend/` applica dietro di loro. Le due app sono identiche per funzioni, regole e dati;
le poche differenze di piattaforma sono al §8. `design/mockup.dc.html` è il riferimento
visivo originale da cui le app sono partite: dove app e mockup divergono, valgono le app e
questo documento le segue. Il contratto degli endpoint fra app e server è `docs/API.md`.

Tre ruoli dietro un unico login: **Cliente**, **Operatore** (staff), **Titolare** (owner).
Attività: barberia con una sola sede a Napoli. Lingua della UI: italiano.

**I dati economici sono del titolare.** Incassi, spesa, importi e totali di visite passate
o future si vedono solo nell'area Titolare. Il cliente vede i prezzi solo mentre prenota
(listino, barre in fondo al wizard, riepilogo, conferma); l'operatore non ne vede mai.

## 1. Autenticazione

### 1.1 Splash
Logo, nome ("Antonio De Vito · Men Care"), barra di caricamento, "Powered by Devora".
L'app si installa come **"ADV MenCare"**; l'icona è il logo completo (monogramma, "Antonio
De Vito", "Men Care") centrato su nero, su entrambe le piattaforme.

### 1.2 Login
- Banda scura, centrata: il logo completo (monogramma, "Antonio De Vito", "Men Care"),
  "Bentornato" e "Il tuo posto in poltrona è a due tap."
- Campi: **Email**, **Password** (mostra/nascondi).
- Errori: credenziali errate ("Email o password non corretti"), errore generico.
- "Password dimenticata?" → §1.4. "Non hai un account? Registrati" → §1.3.
- Login social: "Continua con Google"; su iOS anche Accedi con Apple (§8).
- Un solo login per tutti i ruoli: dopo l'accesso l'app apre l'area del ruolo — Home del
  cliente, Agenda dell'operatore, Agenda del titolare.

### 1.3 Registrazione (solo clienti)
Crea in un passo l'account CLIENT e la sua scheda cliente, poi accede.

| Campo | Regola |
|---|---|
| Nome | obbligatorio |
| Cognome | obbligatorio |
| Numero di telefono | obbligatorio; prefisso fisso `+39` accanto al campo; cifre e spazi, almeno 8 caratteri; salvato come `+39 <numero>` |
| Email | obbligatoria; indirizzo valido; non già registrata ("Questa email è già registrata") |
| Password | almeno 8 caratteri; indicatore di robustezza (Debole / Media / Sicura) |
| Conferma password | deve coincidere (compare "Coincide") |
| Casella termini/privacy | va spuntata |

Gli errori restano nascosti fino al primo tocco su "Crea account", poi si aggiornano mentre
si scrive. Se il salone ha già una scheda cliente con quel numero o quella email e nessun
account collegato — un cliente creato al banco — la registrazione prende quella scheda, così
lo storico resta; se la scheda è già di un account la risposta è "Questo numero è già
collegato a un account". Gli account di operatori e titolare non si auto-registrano: vedi
§4.6.

### 1.4 Recupero password
- Campo: **Email account**. "Invia link di recupero" chiede al server un link di reset
  valido **30 minuti** e utilizzabile una volta sola; la schermata conferma poi "Link
  inviato" con l'indirizzo. La risposta è la stessa che l'indirizzo esista o no, così il
  form non rivela chi è registrato. Usando il link si imposta la nuova password e si esce
  da tutti i dispositivi. La consegna non è ancora collegata: dal server non parte nessuna
  email (§9).
- "Altre vie": codice via SMS al numero mascherato dell'account, o messaggio WhatsApp al
  salone — per ora nessuna delle due manda niente (§9).

### 1.5 Logout
"Esci dall'account" in fondo a ogni pagina profilo (cliente, operatore, titolare).

## 2. Area Cliente — tab Home · Prenota · Appuntamenti · Profilo

### 2.1 Home
- Banda scura: logo a sinistra, campanella con pallino dei non letti a destra (§5); saluto
  "Ciao <nome>" e contatore visite ("14 visite").
- Card **prossimo appuntamento**: countdown ("tra 4 ore", "oggi"), ora, giorno e durata
  totale, operatore, servizi, "Al calendario". Nessun prezzo. Senza prenotazioni: "Nessun
  appuntamento in programma".
- **"Prenota ora"**, centrato: apre il wizard.
- **"Primi slot liberi"**: fino a 4 chip con i primi orari prenotabili del salone su
  qualsiasi operatore (oggi e i 7 giorni successivi, al massimo 2 al giorno), dimensionati
  sui servizi dell'ultima visita completata — o su un servizio in evidenza per chi non ha
  storico. Un tocco apre il wizard sullo step data con servizi, giorno e ora già scelti; il
  wizard richiede prima la disponibilità, quindi se nel frattempo quell'ora è stata presa
  resta selezionato il giorno e il cliente sceglie un altro orario.
- **"Riprenota in un tap"**: l'ultima visita completata (servizi, operatore, durata) con
  "Riprenota" (apre il wizard sullo step data con operatore e servizi scelti) e il link
  "Storico" al tab Appuntamenti.

### 2.2 Wizard di prenotazione (4 step)
Titolo "Nuova prenotazione" ("Modifica prenotazione" in modifica), indicatore degli step
(Operatore · Servizi · Data · Conferma), "Annulla" a ogni step riporta al tab Appuntamenti.
Negli step 1–3 la barra in basso con "continua" sale solo quando lo step ha una scelta
valida e scende se la scelta viene tolta.

1. **Operatore** — "Qualsiasi operatore" ("Il doppio degli orari disponibili") o uno del
   team ("Il team · N operatori"). Card operatore: iniziali e nome, mansione ("Barbiere").
   Gli operatori senza posti liberi nei prossimi 14 giorni sono sbiaditi ma selezionabili;
   la verifica usa uno dei servizi dell'operatore, quindi vuol dire "questo operatore non
   ha niente di libero", non "niente di libero per i servizi che sceglierai". Cambiando
   operatore cadono i servizi già scelti che quell'operatore non esegue, e si azzerano
   giorno e ora.
2. **Servizi** — nome dell'operatore scelto con "Cambia" (torna allo step 1). Lista piatta
   dei servizi che esegue (tutti con "Qualsiasi"), senza categorie: nome, durata, prezzo.
   Selezione multipla; la barra li somma ("2 servizi · 75 min · € 22,00"). Aggiungere o
   togliere un servizio azzera giorno e ora: gli slot dipendono dalla durata totale.
3. **Data** — calendario mensile che parte dal lunedì; mese precedente disabilitato prima
   di quello corrente. Stati del giorno:
   - *disponibile* (pieno, pallino oliva): almeno uno slot ospita i servizi scelti;
   - *al completo* (solo bordo, senza pallino): il salone è aperto e l'operatore (o gli
     operatori) lavorano quel giorno, ma ogni orario è preso;
   - *non selezionabile* (sbiadito): giorni passati, chiusura del salone, giorni in cui
     l'operatore non lavora o è bloccato.

   Scegliendo un giorno disponibile compare la griglia degli orari (4 per riga, legenda
   "libero"/"scelto") per la durata **totale** dei servizi scelti; gli orari presi non
   compaiono. La barra mostra giorno, ora, durata e totale.

   Scegliendo un giorno al completo, al posto della griglia compare la pagina **"Giornata
   al completo"**: un'illustrazione animata (un calendario con tutti i giorni barrati e
   un cronometro con la lancetta che gira), la frase "Tutti gli slot orari sono già
   prenotati. Vuoi essere avvisato quando si libera un posto?" e il pulsante **"Avvisami"**,
   fisso in fondo allo schermo come le barre degli altri step. "Avvisami" mette il
   cliente in lista d'attesa per quel giorno a qualsiasi orario, con l'operatore scelto
   (o qualsiasi) e i servizi scelti (§6.4); il pulsante diventa "Ti avviseremo · N° in
   fila", con l'indicazione che la richiesta si trova in Appuntamenti. Tornando su un
   giorno in cui si è già in lista, compare direttamente lo stato "in lista".
4. **Riepilogo** — card di riepilogo (data, ora, durata, operatore), dettaglio per servizio
   con prezzi e totale, **"Note per l'operatore"** facoltative (max 200 caratteri, contatore).
   "Conferma prenotazione".

**Orario preso durante la conferma**: se nel frattempo qualcun altro prende l'orario, il
cliente torna allo step 3 con la disponibilità aggiornata e il messaggio "Questo orario è
appena stato prenotato. Scegline un altro.", più una card per mettersi in lista per quello
stesso orario ("Mettimi in lista" → "N° in fila"). Se era l'ultimo posto del giorno, il
giorno mostra invece la pagina "Giornata al completo".

**Modifica** ("Modifica" su un appuntamento futuro): il wizard riparte dallo step 1 con
operatore, servizi e nota già compilati. In modifica l'appuntamento lascia libero il
proprio posto, quindi il suo giorno e il suo orario restano sceglibili. Confermando, il
server lo **sostituisce** in un colpo solo: l'originale viene annullato (dal cliente) e il
nuovo creato insieme, così una prenotazione che fallisce lascia intatto l'originale.

### 2.3 Conferma
Pagina scura a tutto schermo: spunta, "Prenotazione confermata", "Ti aspettiamo <giorno>
alle ore <ora>", riepilogo (l'operatore effettivamente assegnato — anche quando si era
scelto "Qualsiasi operatore" — servizi, durata, totale, nota se c'è), "Aggiungi al
calendario", "Torna alla Home".

### 2.4 Appuntamenti
Tab "Prossimi · N" e "Passati · N". La divisione è sull'ora di **fine**: un appuntamento
resta fra i "Prossimi" finché non è terminato, e la Home continua a mostrarlo come
prossimo mentre è in corso.
- Card **prossimo**: blocco data, ora, badge "Confermato", servizi, operatore e durata;
  azioni **Modifica** (§2.2) e **Annulla** (dialogo "Annullare l'appuntamento?" →
  annullato dal cliente).
- Sezione **Lista d'attesa**, sotto le card dei prossimi quando il cliente è in coda: una
  riga per richiesta con giorno, ora o "qualsiasi orario", operatore o "Qualsiasi
  operatore", "in attesa", posizione in coda ("1° in fila") ed **Esci** per lasciare la
  coda. Compaiono solo le richieste ancora in attesa per un giorno futuro: quando il
  cliente è stato avvisato di un posto libero, o il giorno è passato, la riga sparisce
  (§6.4).
- Righe **passate**: blocco data (spento se la visita non c'è stata), servizi, ora,
  operatore, "Riprenota"; gli annullati dicono chi ha annullato ("Annullato dal cliente" /
  "Annullato dal salone"), i no-show "No-show".
- Striscia statistiche sotto i passati: visite completate (appuntamenti segnati come
  completati), operatore preferito (nome di chi ha più visite completate) e giorni medi tra
  una visita e l'altra (media degli intervalli fra i giorni distinti in cui il cliente è
  venuto).
- Tab prossimi vuoto: breve testo, "Prenota ora" e "Riprenota l'ultimo".

### 2.5 Profilo
- Banda: iniziali, nome e cognome, "Cliente dal <mese anno> · N visite", **Modifica**/**Salva**.
- **Dati personali**: Nome, Cognome, Email, Telefono — righe in sola lettura, campi
  modificabili dopo "Modifica"; "Salva modifiche" salva.
- **Orari del salone**: gli orari di apertura impostati dal titolare (giorni consecutivi con
  lo stesso orario raggruppati, "chiuso" per i giorni di chiusura) e l'indirizzo del salone.
- **Gestione profilo · Password** → foglio "Cambia password": Password attuale
  (obbligatoria; il server la verifica e risponde "Email o password non corretti" sul campo
  quando è sbagliata), Nuova password (almeno 8 caratteri, almeno una lettera e una cifra,
  indicatore di robustezza), Conferma nuova password (deve coincidere). Il cambio fa uscire
  tutti gli altri dispositivi; quello da cui si cambia resta dentro.
- **Notifiche** (preferenze del cliente, per account):
  - "Promemoria appuntamento" (24 ore prima · push) — default ON
  - "Slot liberi in lista d'attesa" — default ON
  - "Promozioni e novità" (consenso marketing) — default OFF
- "Esci dall'account".

## 3. Area Operatore (staff) — tab Agenda · Clienti · Profilo

### 3.1 Agenda
- Solo gli appuntamenti dell'operatore collegato. Banda: foto o iniziali (tocco →
  Profilo), nome, mansione, campanella (§5).
- Sotto, la **barra dei giorni** (§7b), lo stesso comando dell'agenda del titolare
  (§4.3): frecce giorno precedente/successivo, data del giorno scelto, "Oggi" quando il
  giorno scelto non è oggi, e la striscia di sei giorni attorno. A giornata vuota la riga
  "Nessun appuntamento · tocca un orario per prenotare" sta sotto la barra.
- Griglia oraria del giorno, uguale a una colonna dell'agenda del titolare (§4.3): scala
  delle ore con le mezz'ore — le righe passano anche sotto le ore, così scala e griglia
  sono un tutt'uno — sempre visibile, anche a giornata vuota ("Nessun appuntamento
  · tocca un orario per prenotare"). Card alte quanto la durata: cliente, fascia oraria (o
  "in corso"), servizi, etichetta "walk-in"; oliva se prenotato o in corso, pietra
  se completato, spenta con il filo e la scritta "No-show" se il cliente non è venuto —
  resta visibile per poterla correggere. I blocchi (§3.2) sono una fascia grigia su tutta la colonna con
  etichetta o motivo al centro ("Pausa pranzo", "Ferie"…), la stessa dell'agenda del
  titolare.
- Tocco su una card → dettaglio appuntamento (§3.3). Tocco su uno spazio libero → foglio
  "Nuovo appuntamento" con quel giorno e quel quarto d'ora già impostati (§3.5); i tocchi
  sui blocchi e sulle fasce fuori turno non fanno nulla.
- Pulsanti "Ferie e permessi" (§3.2) e "Nuovo appuntamento" (§3.5).

### 3.2 Ferie e permessi
Bottom sheet, uguale a quello del titolare (§4.3) senza la scelta dell'operatore (è sempre
quello collegato), alto quanto il contenuto con il pulsante subito sotto.
- **Motivo**: Permesso · Pausa · Ferie · Corso.
- **Data** (calendario), **Dalle ore** / **Alle ore** (rotelle); scorciatoie "Mezza
  giornata" (09:00–13:00) e "Giornata intera" (09:00–19:00).
- **Conflitti**: gli appuntamenti che cadono nella fascia sono elencati ("<cliente> · <ora>
  · <servizi>. Va spostato o passato a un collega.") e vanno risolti prima di salvare:
  "Passa a <collega>" sposta l'appuntamento a quel collega alla stessa ora; "Proponi altro
  orario" lo segna come gestito.
- CTA "Blocca 15:30 – 18:00". Un blocco copre un giorno; la sua fascia sparisce dagli
  orari offerti ai clienti.

### 3.3 Dettaglio appuntamento
- Banda scura: iniziali del cliente (Olive Wood su nero), nome, stato ("Confermato", "in
  corso", "Completato ✓", "No-show"), visite totali.
- **Modifica** (in alto, solo finché l'appuntamento è in programma): il foglio "Modifica
  appuntamento" (§3.5) precompilato con cliente, giorno, ora e servizi; salvando
  l'appuntamento viene sostituito (§6.2) e si torna all'agenda.
- Riquadri: fascia oraria e durata (nessun prezzo). Servizi con la loro durata.
- **Nota del cliente**: la nota scritta nel riepilogo della prenotazione, quando c'è.
- **Storico visite**: le altre visite completate del cliente (data, servizi; nessun importo).
- Azioni: "Annulla appuntamento" (dialogo; risulta annullato dal salone e il cliente riceve
  la notifica, a meno che il titolare non abbia spento "Annullamento" al §4.7), "Chiama"
  (apre il tastierino con il numero del cliente; disattivato se il cliente non ne ha uno),
  principale **"Segna completato"** — serve solo per chiudere un appuntamento prima della
  fine o per correggere un no-show, perché gli stati avanzano da soli (§6.2).
- **"Non si è presentato"**, passato l'orario d'inizio — anche su un appuntamento già
  chiuso come completato: un dialogo chiede conferma ("Il cliente riceve una notifica…") e
  il cliente viene avvisato. Un no-show segnato per sbaglio torna indietro con "Segna
  completato".

### 3.4 Clienti
Stessa lista e stessa scheda cliente del tab Clienti del titolare (§4.9), senza il
riquadro della spesa e senza importi; "Nuova prenotazione" riporta all'agenda.

### 3.5 Nuovo appuntamento (operatore)
Bottom sheet, uguale alla prenotazione manuale del titolare (§4.4) con l'operatore fisso su
quello collegato e la lista dei servizi limitata a quelli che quell'operatore esegue. Come
ogni prenotazione, si fa sulla griglia da 30 minuti e con lo stesso preavviso di 30 minuti
del cliente (§6.1): la collocazione libera vale solo per spostare un appuntamento che
esiste già (§6.3). Aperto da "Modifica" nel dettaglio appuntamento (§3.3) diventa "Modifica
appuntamento": il cliente è fisso, il modulo è precompilato, l'orario dell'appuntamento
resta sceglibile e "Salva modifiche" lo sostituisce (§6.2).

### 3.6 Profilo
Foto o iniziali (tocco → selettore foto di sistema; "Rimuovi foto" riporta le iniziali),
nome e mansione; righe account Email e Telefono (sola lettura); "Esci dall'account".

## 4. Area Titolare — tab Dashboard · Clienti · Agenda · Gestione · Profilo
Dopo l'accesso il titolare atterra sul tab Agenda.

### 4.1 Dashboard
Tutte le cifre le calcola il server sul periodo scelto; niente è gonfiato, e un periodo
senza lavoro mostra zeri.
- Selettore di periodo Giorno · Settimana · Mese (default Giorno): Giorno = oggi, Settimana
  = da lunedì a domenica della settimana corrente, Mese = il mese di calendario.
- **Incassi**: totale degli appuntamenti **segnati come completati** che iniziano nel
  periodo, con l'andamento rispetto alla finestra precedente della stessa lunghezza
  ("Incassi del mese € 9.840 · +12%"). Se nella finestra precedente non c'era niente,
  l'andamento non viene inventato: è 0.
- **Appunt.**: tutti gli appuntamenti non annullati del periodo, di ogni operatore, no-show
  compresi.
- **No-show %**: i no-show su quegli appuntamenti, con un decimale.
- **Scontrino medio**: incassi divisi per il numero di appuntamenti completati.
- "Occupazione per operatore" (barre): minuti prenotati sui minuti in cui l'operatore
  poteva davvero lavorare nel periodo — il suo orario intersecato con quello del salone,
  meno i blocchi, con le ferie non conteggiate affatto — con il tetto al 100%.
- **"Prossimi 7 giorni"** (oggi compreso, qualunque sia il periodo): una colonna per giorno
  con l'occupazione del salone — minuti prenotati sui minuti in cui tutti gli operatori
  possono lavorare quel giorno, esclusi annullati e no-show — oliva dall'80%, "chiuso" nei
  giorni di chiusura; un bollino oliva con il numero di clienti in lista d'attesa per quel
  giorno ("Avvisami") e una riga con il totale ("aspettano un operatore al completo": una
  richiesta aspetta un operatore preciso — o qualsiasi — che è pieno, mentre la barra è
  tutto il salone, quindi un giorno con persone in attesa può avere ancora posto). Sotto,
  un pulsante oro a tutta larghezza apre la campagna push (§4.2): "Riempi i giorni vuoti
  con una campagna" se un giorno aperto è sotto il 50%, "Invia campagna push" altrimenti.
  È l'unico ingresso alla campagna.
- Tutta la dashboard sta in una schermata del telefono; non c'è il riquadro dei clienti
  inattivi (il segmento "Inattivi 60gg" resta nella campagna).

### 4.2 Campagna push
- Badge "Bozza", **Nome campagna**, copertura "N di M raggiungibili": M è la dimensione del
  segmento, N la parte che si riesce davvero a raggiungere — cliente con consenso marketing
  sulla scheda, un account nell'app e "Promozioni e novità" acceso nel profilo (§2.5). È lo
  stesso conteggio usato per l'anteprima e per l'invio, così non possono discordare.
- **Destinatari**: Inattivi 60gg · Tutti · Top spesa (§6.5).
- **Titolo** e **Messaggio** (max 140 caratteri, contatore), token "+ Nome cliente"
  (`{{nome}}`) e "+ Link prenota" (`{{link}}`, deep link alla prenotazione); anteprima
  della push con i token compilati.
- **Schedulazione**: "Invia ora" o "Programma" (data + ora, nell'orario del salone);
  interruttore "Ripeti ogni settimana", che rimanda la campagna alla stessa ora per un
  massimo di 4 esecuzioni ("Stop dopo 4 invii"). Anche il tetto agli invii può fermarla
  prima: conta i destinatari raggiunti dalla campagna in totale, non per singola
  esecuzione.
- CTA "Invia ora" / "Programma invio". Nome e messaggio sono obbligatori; una campagna che
  non raggiunge nessuno viene rifiutata invece di partire a vuoto.
- L'invio è messo in coda, mai fatto dentro il tocco: i destinatari si calcolano al momento
  dell'invio (così chi nel frattempo ha tolto il consenso resta fuori), a nessun cliente si
  scrive due volte nello stesso giorno, e a esecuzione finita il titolare riceve la notifica
  in app "Campagna inviata". Una campagna già inviata non si modifica più.

### 4.3 Agenda
- Banda: "Agenda settimanale", "N operatori · N appuntamenti", campanella (§5); sotto, la
  **barra dei giorni** (§7b) — frecce giorno precedente/successivo, data del giorno scelto,
  "Oggi" quando il giorno scelto non è oggi, e la striscia di sei giorni attorno. È lo
  stesso comando dell'agenda operatore (§3.1): le due agende navigano il tempo allo stesso modo.
- Una colonna per operatore (iniziali, nome) su una scala delle ore con le mezz'ore;
  altezza della card ∝ durata — una card sotto i 45 minuti mostra solo il nome del
  cliente; le righe delle ore passano anche sotto la scala. Le card usano gli
  stessi colori dell'agenda dell'operatore (§3.1): olive se attivo, stone se completato,
  spenta con il filo e la scritta "No-show" se il cliente non è venuto — una card diventa
  nera solo mentre la si trascina; gli annullati non compaiono. Blocchi e fasce in cui un operatore non è in
  turno hanno lo stesso aspetto: una fascia grigia su tutta la colonna, etichetta al
  centro — etichetta o motivo del blocco ("Pausa pranzo", "Ferie"…), "Riposo" su un
  giorno libero intero. Una colonna vuota non deve sembrare prenotabile. La striscia degli operatori scorre
  insieme alle colonne; quando le colonne non entrano tutte nello schermo, le corsie si
  allargano a riempirlo oppure una freccia tonda sulla striscia segnala — e porta a —
  gli operatori fuori schermo.
- Tocco su uno spazio libero → prenotazione manuale (§4.4) con operatore, giorno e quarto
  d'ora impostati; i tocchi sui blocchi e sulle fasce fuori turno non fanno nulla. Pulsanti flottanti "Ferie e
  permessi" e "Nuova prenotazione".
- **Drag & drop**: pressione prolungata su una card e trascinamento su un altro orario e/o
  un'altra colonna; si aggancia ai 15 minuti e una targhetta segue il dito con la
  destinazione ("Su Luca alle 10:15", "Su Giulia alle 10:15 · non in turno" su una fascia fuori
  turno, oppure "Fuori dalla giornata" fuori dalla scala). Si
  posa solo dove c'è posto (§6.3): se riesce compare "Appuntamento spostato" e il cliente
  riceve la notifica, altrimenti "Slot non disponibile" e la card torna al suo posto.
  Mentre si trascina, i pulsanti flottanti spariscono.
- Tocco su una card → bottom sheet: cliente (iniziali Olive Wood su nero), ora · durata ·
  operatore, prezzo, stato (Confermato · In corso · Completato · No-show), servizi,
  indicazione per spostarla trascinandola, "Chiama" in oro (apre il tastierino con il
  numero del cliente; compatto accanto a un **Modifica appuntamento** più largo, a tutta
  larghezza su un appuntamento completato), e "Annulla appuntamento" (il secondo tocco
  conferma; annullato dal salone, con la notifica al cliente come al §3.3). Passato
  l'orario d'inizio — anche dopo che l'appuntamento si è chiuso da solo come completato —
  compare **"Non si è presentato"** (il secondo tocco conferma; il cliente viene avvisato);
  su un no-show, **"Segna come completato"** lo corregge. Un appuntamento completato o
  no-show non offre "Modifica" né "Annulla".
  "Modifica" apre il foglio della prenotazione manuale (§4.4) precompilato con cliente,
  operatore, giorno e servizi; l'appuntamento lascia libero il proprio posto, quindi il suo
  orario resta sceglibile, e il salvataggio lo **sostituisce** in un colpo solo (annullato
  dal salone): un salvataggio fallito lascia l'agenda com'era.
- Foglio **Ferie e permessi**: Operatore, Motivo (Permesso · Pausa · Ferie · Corso), Data,
  Dalle ore / Alle ore, "Mezza giornata" / "Giornata intera"; gli appuntamenti in
  conflitto vengono contati ("N appuntamenti in conflitto — Spostali o annullali
  dall'agenda prima di bloccare questa fascia") e impediscono il salvataggio. CTA
  "Blocca <fascia>".

Su tablet l'agenda usa tutta la larghezza per le colonne degli operatori (§7).

### 4.4 Prenotazione manuale
Bottom sheet "Nuova prenotazione" (titolare) / "Nuovo appuntamento" (operatore), "Annulla"
e "Salva" nella barra.
- **Cliente**: ricerca istantanea per nome o telefono; ogni risultato mostra telefono e
  visite. "Crea nuovo cliente" apre i campi Nome, Cognome, Telefono (una scheda cliente
  senza account nell'app e senza email).
- **Operatore** (solo titolare): menu a tendina con tutti gli operatori, spunta su quello scelto.
- **Data**; **Servizi**: tendina a selezione multipla gemella di quella dell'operatore,
  una spunta per servizio scelto, il campo li riassume.
- Quando operatore e servizi scelti non danno nessun orario, la riga vuota dice perché:
  "Questo operatore non esegue i servizi scelti" oppure "L'operatore non è in turno in
  questo giorno" — una colonna vuota in agenda non vuol dire prenotabile.
- In modifica (§4.3) il cliente dell'appuntamento è una riga fissa: niente ricerca, niente
  "Crea nuovo cliente".
- **Orario**: tutti gli orari liberi del giorno per i servizi scelti, su un'unica riga
  scorrevole; l'etichetta mostra la durata totale. Aperto da un tocco in agenda, l'orario
  toccato si seleziona appena i servizi ci stanno ("Ore 10:30 · scegli i servizi per
  confermare" fino ad allora; "Alle 10:30 non c'è posto per questi servizi: scegli un
  altro orario" se non ci stanno).
- **"Invia SMS di conferma"** ("Il cliente non ha l'app") — default ON.
- CTA "Inserisci in agenda". L'appuntamento viene registrato come prenotato per telefono.

### 4.5 Gestione · Servizi
- Sotto-tab: Servizi · Operatori · Notifiche · Orari.
- "Listino · N": lista piatta; riga = nome, "<durata> min · N operatori", prezzo. Tocco → modifica.
- Editor del servizio ("Nuovo servizio" / "Modifica servizio"): **Nome** (obbligatorio),
  **Durata** con stepper (5–240 min), **Prezzo (€)**, **Operatori abilitati** (checklist —
  decide quali operatori e servizi offre il wizard). Il salvataggio riscrive quella lista
  nei due sensi: l'operatore spuntato acquisisce il servizio, quello tolto lo perde. I
  servizi non si cancellano, si disattivano, così gli appuntamenti passati mantengono il
  loro storico. "+ Nuovo servizio" in fondo.

### 4.6 Gestione · Operatori
- Lista: iniziali, nome, "<mansione> · N servizi", badge "Titolare" sul titolare; "+ Nuovo
  operatore" in fondo.
- Tocco → la card si espande (sola lettura): **Orario settimanale** (giorni raggruppati,
  "chiuso"), **Ferie e blocchi** (blocchi futuri raggruppati in periodi), **Servizi
  assegnabili** (chip).
- **Nuovo operatore**: Nome e cognome (obbligatorio), Ruolo (testo libero, suggerimento
  "Barbiere, hair stylist…"), Email di accesso (obbligatoria, valida, non già usata),
  Telefono, **Giorni di lavoro** (sette chip), Dalle ore / Alle ore (la stessa fascia per
  ogni giorno di lavoro), **Servizi che esegue** (almeno uno). Il salvataggio crea anche
  l'account STAFF dell'operatore con quell'email e una password provvisoria, che il server
  restituisce una volta sola nella risposta al salvataggio: non parte nessuna email di
  invito, quindi la password va consegnata a mano (§9).

### 4.7 Gestione · Notifiche
- **Promemoria automatici**: elenco ordinato ("1° promemoria", "2° promemoria"…). Il 1° è
  "Un giorno prima" o "2 giorni prima"; i successivi "2 ore prima" o "4 ore prima"; ogni
  valore si può usare una volta sola. "Aggiungi promemoria" ne aggiunge uno non ancora
  usato; ognuno si può rimuovere. Una regola è "N ore prima dell'appuntamento", quindi il
  server accetta qualsiasi numero intero di ore da 1 a 168 anche se l'app ne propone
  quattro.
- I promemoria partono solo verso i clienti che hanno lasciato acceso "Promemoria
  appuntamento" (§2.5), una volta per appuntamento e per regola, e mai per una prenotazione
  fatta quando il momento di quella regola era già passato (per una prenotazione presa 30
  minuti prima non parte il promemoria a 2 ore). Non c'è una fascia notturna: un promemoria
  a 2 ore per un appuntamento alle 09:00 parte alle 07:00 (§9).
- **Tipi di notifica** (interruttori): Conferma prenotazione e Annullamento decidono se il
  **cliente** viene avvisato quando una prenotazione è confermata o quando il salone
  annulla; l'operatore viene avvisato in ogni caso. Operatore in ritardo e Promozioni nei
  giorni vuoti si salvano ma nessuno li usa ancora (§9) — entrambi OFF di default.
- Anteprima push ("Ci vediamo domani alle 17:30 con Antonio. Rispondi per spostare.").

### 4.8 Gestione · Orari
Una card per giorno della settimana: interruttore Aperto/Chiuso, Dalle ore / Alle ore (una
fascia al giorno). Gli orari del salone delimitano gli orari prenotabili di ogni
operatore. "Salva orari".

### 4.9 Clienti (CRM)
- Ricerca per nome, telefono o email; filtri Tutti · Inattivi 60+; sezioni alfabetiche per
  cognome; le righe mostrano visite e ultima visita.
- Scheda cliente: iniziali, nome, telefono, "cliente dal <anno>"; riquadri Visite, Spesa
  totale, No-show; **"Abitudini"** — "Operatore preferito" (chi ha più visite completate,
  a parità il più recente) e "Torna in media ogni N giorni" (distanza media fra le visite
  completate, per difetto), ciascuno solo quando lo storico basta per dirlo; "Storico
  appuntamenti" (data, servizi, operatore, importo); in una barra fissa in fondo (fondo
  pieno, filo e ombra sopra, sopra lo storico che scorre) "Chiama" (apre il tastierino con il
  numero del cliente) e "Nuova prenotazione", che apre la prenotazione manuale per quel
  cliente. L'operatore vede le stesse abitudini (§3.4).

### 4.10 Profilo
Foto (selettore di sistema, "Rimuovi foto"), nome; **Account**: Email, Telefono; **Salone**:
Nome, Indirizzo (sola lettura); "Esci dall'account".

## 5. Notifiche (tutti i ruoli)
- Una sola pagina, aperta dalla campanella (Home del cliente, Agenda dell'operatore,
  Agenda del titolare); ogni account vede solo le proprie notifiche, e scorrendo si
  caricano le più vecchie.
- Righe: titolo, testo, data e ora, un pallino — Olive Wood pieno se non letta all'apertura
  della pagina, sbiadito se già letta. Aprire la pagina segna tutto come letto e spegne il
  pallino della campanella.
- Cosa produce davvero una notifica, e per chi:

  | Evento | Chi viene avvisato |
  |---|---|
  | Prenotazione confermata | il cliente (se l'interruttore "Conferma prenotazione" del salone è acceso) e l'operatore |
  | Appuntamento spostato | il cliente |
  | Annullato dal salone | il cliente (se "Annullamento" è acceso) |
  | Annullato dal cliente | l'operatore |
  | Promemoria, N ore prima (§4.7) | il cliente, se "Promemoria appuntamento" è acceso |
  | Si libera un posto in un giorno per cui il cliente è in coda (§6.4) | quel solo cliente, se "Slot liberi in lista d'attesa" è acceso |
  | Campagna push (§4.2) | ogni cliente raggiungibile; al titolare arriva "Campagna inviata" a fine esecuzione |
  | Appuntamento segnato no-show | il cliente |

  Gli stati che avanzano da soli (in corso, completato) e la correzione di un no-show non
  avvisano nessuno. Le notifiche
  restano per sempre: niente le cancella (§9).
- Il testo lo scrive il server, così le due app mostrano le stesse parole.

## 6. Regole di business

### 6.1 Slot
- Uno slot è un orario d'inizio per la durata **totale** dei servizi scelti, su una
  griglia di 30 minuti, dentro l'orario settimanale dell'operatore intersecato con gli
  orari di apertura del salone, tolti appuntamenti attivi, blocchi e ferie dell'operatore.
  Gli orari del salone vincono sempre: il turno che sporge fuori non è prenotabile.
- La griglia riparte dall'inizio di ogni fascia di lavoro, quindi un turno spezzato
  09:00–13:00 / 14:00–19:00 offre 14:00 e 14:30, non le 13:30.
- Per prenotare in giornata servono 30 minuti di anticipo; i giorni passati non offrono
  niente.
- **Queste regole valgono per ogni prenotazione, da chiunque venga fatta**: il wizard del
  cliente, il foglio dell'operatore e la prenotazione manuale del titolare. Solo lo
  spostamento di un appuntamento già in agenda è libero (§6.3).
- "Qualsiasi operatore" = unione degli slot di tutti gli operatori che eseguono **tutti** i
  servizi scelti; il server assegna il primo libero a quell'ora e l'app mostra su chi è
  finito. Se quell'operatore viene occupato mentre la prenotazione si scrive, il server
  prova il successivo prima di arrendersi. Se quella combinazione di servizi non la esegue
  nessuno la risposta lo dice ("Questo operatore non esegue i servizi scelti"), invece di
  far credere che l'orario fosse preso.
- L'abilitazione servizio ↔ operatore filtra sia la lista dei servizi sia gli operatori.
- La disponibilità viene ricontrollata mentre la prenotazione si scrive, e il database
  stesso rifiuta due appuntamenti sovrapposti sullo stesso operatore: chi conferma per
  secondo si sente dire che l'orario è appena andato ("orario preso durante la conferma").

### 6.2 Appuntamenti
- Stati: **Confermato** → **In corso** → **Completato**, oppure **Annullato** / **No-show**.
  Ogni prenotazione è confermata subito: non c'è un passaggio di approvazione.
- **Gli stati avanzano da soli**: all'orario d'inizio un appuntamento confermato diventa
  "in corso", e a inizio + durata, se nessuno l'ha gestito, "completato" — il server
  controlla ogni minuto, così nessuno deve spostare gli stati a mano. "Segna completato"
  resta per chiuderne uno in anticipo.
- **Il no-show si segna a mano**: "Non si è presentato", solo passato l'orario d'inizio,
  anche su un appuntamento già chiuso come completato; il cliente riceve una notifica. Un
  no-show segnato per sbaglio torna completato ("Segna come completato"). Reimpostare lo
  stato che un appuntamento ha già non cambia niente, quindi nessuna visita si conta due
  volte. Un appuntamento annullato non si recupera.
- Un annullamento registra chi ha annullato: il cliente (dall'app) o il salone
  (operatore/titolare). Il cliente può annullare fino a **2 ore** prima dell'inizio; più
  sotto l'app gli dice di chiamare il salone. "Modifica" sostituisce il vecchio
  appuntamento con il nuovo (qui sotto), quindi alle modifiche vale la stessa finestra. Il
  salone non ha limiti.
- **Modifica = sostituzione** (wizard del cliente, foglio del titolare e dell'operatore):
  in modifica la disponibilità ignora l'appuntamento che si sta modificando, quindi il suo
  orario resta sceglibile; il salvataggio annulla il vecchio e crea il nuovo nella stessa
  transazione del server — se il nuovo non entra, non cambia niente.
- Un appuntamento conserva la durata e il prezzo totale con cui è stato prenotato, anche se
  poi i servizi cambiano.
- Canale: **App** (wizard del cliente), **Telefono** (prenotazione manuale di operatore o
  titolare), **Walk-in** (etichetta nell'agenda dell'operatore).
- "Segna completato" aggiorna visite, spesa totale e ultima visita del cliente, i KPI del
  titolare e il segmento "Inattivi 60+".

### 6.3 Spostamenti manuali
Spostare un appuntamento che esiste già è libero per il salone: si aggancia ai 15 minuti e
deve solo rientrare nell'orario di lavoro dell'operatore di destinazione, nelle sue ferie,
nei suoi blocchi e negli altri appuntamenti; la griglia di 30 minuti e l'anticipo non
valgono. Il titolare può spostare qualsiasi appuntamento, a un altro orario o nella colonna
di un altro operatore; l'operatore può spostare solo quelli della propria colonna e non può
passarli a un collega dall'agenda (per questo c'è "Passa a <collega>" al §3.2). Il cliente
che modifica dall'app resta sulla griglia normale e tiene lo stesso operatore.

### 6.4 Lista d'attesa
- Una richiesta vale per **un giorno**, per un orario preciso ("orario preso durante la
  conferma") o per qualsiasi orario ("Avvisami" su un giorno al completo), per un
  operatore preciso o qualsiasi, per i servizi scelti.
- Una sola richiesta per cliente, giorno e scelta di operatore: rimettersi in coda per lo
  stesso giorno e lo stesso operatore aggiorna la richiesta che c'è già — orario, servizi e
  durata — e conserva il posto in coda. "Qualsiasi operatore" è una scelta a sé, quindi un
  cliente può avere una richiesta su Antonio e una su "qualsiasi" per lo stesso giorno, in
  due code diverse.
- Coda = richieste per lo stesso giorno e la stessa scelta di operatore, in ordine di
  arrivo; la posizione è aggiornata in tempo reale e scende quando chi sta davanti esce o
  viene servito.
- Un posto si libera quando un appuntamento viene annullato o spostato altrove, o quando un
  blocco viene cancellato. Le richieste di quel giorno vengono allora lette in ordine di
  arrivo e **una** sola persona viene avvisata: la prima i cui servizi ci stanno —
  all'orario esatto richiesto, se ne aveva chiesto uno, altrimenti al primo orario libero
  del giorno. Quella richiesta esce dalla coda; il posto che si libera dopo va alla persona
  successiva. Il posto non viene trattenuto: lo prende chi prenota per primo.
- Il cliente che ha spento "Slot liberi in lista d'attesa" viene saltato e conserva il suo
  posto in coda; l'avviso va alla persona dopo. Una richiesta inserita al banco per un
  cliente senza account nell'app viene chiusa senza avviso, perché non c'è dove mandarlo.
- Le richieste scadono quando il loro giorno è passato; quelle già avvisate scadono dopo 24
  ore. Niente riapre una richiesta scaduta, e se chi è stato avvisato non prenota nessun
  altro viene chiamato.
- Cambiare l'orario di un operatore o togliere delle ferie non va a cercare nessuno da
  avvisare: lo fanno solo i tre eventi qui sopra (§9).

### 6.5 Clienti e segmenti
Tutti i conteggi dietro questi segmenti vengono dagli appuntamenti: visite, spesa, no-show
e ultima visita non sono mai salvati sul cliente.
- **Tutti**: ogni scheda cliente, con o senza account nell'app.
- **Inattivi 60+**: nessuna visita completata negli ultimi 60 giorni, o mai una.
- **Fedeli**: almeno 10 visite completate e una di queste negli ultimi 60 giorni.
- **No-show**: almeno un no-show.
- **Top spesa**: il 10% che ha speso di più fra i clienti che hanno speso qualcosa — una
  definizione mobile, non una cifra fissa.
- La lista CRM (§4.9) offre Tutti e Inattivi 60+; le campagne (§4.2) offrono Tutti,
  Inattivi 60gg e Top spesa. Fedeli e No-show esistono nei dati ma nessuna schermata li
  propone ancora (§9).
- Una campagna raggiunge un cliente solo se valgono tutte e tre le cose: consenso marketing
  sulla scheda, un account nell'app e "Promozioni e novità" acceso nel profilo (§2.5).

## 7. Tablet
- Entrambe le app girano su tablet (iPad, tablet Android) a tutto schermo in ogni
  orientamento; i telefoni restano in verticale — la soglia è una larghezza minima di
  600 dp.
- I layout del telefono restano, centrati: bande scure, barre e sfondi occupano tutta la
  larghezza, il contenuto sta in una larghezza di lettura di 640; i bottom sheet hanno lo
  stesso limite.
- Eccezione: l'agenda del titolare (§4.3) usa tutta la larghezza per le colonne degli operatori.

## 7b. Design system

Le due app condividono un solo insieme di token (`core/designsystem` su Android,
`Core/DesignSystem` su iOS); un token cambia in tutt'e due, nello stesso commit.

- **Colore.** Ogni token regge almeno 4.5:1 (WCAG 2.1 AA, testo normale) sulla superficie
  su cui l'app lo mette davvero — Stone `#EBEBEA` è la più severa delle due superfici
  chiare, quindi è quella su cui sono tarati i toni di testo. L'accento ha due ruoli che
  non vanno scambiati: `OliveWood` `#77654B` su superficie chiara (testo, icone, bordi e i
  riempimenti che portano testo Bone), `OliveLight` `#BFA277` sulle bande near-black — è
  l'oro della scritta "MEN CARE" del logo, campionato da `design/logo-lockup.png`. Il testo
  secondario è `TextMuted` sul chiaro e `OnDarkMuted` sullo scuro, mai un Bone sbiadito. Le
  pill d'accento usano il tono pieno `OliveTint`, non l'accento con un'alpha, così il loro
  contrasto non dipende da cosa c'è sotto.
- **Tipografia.** Jost ha le aste sottili e Cormorant è un serif ad alto contrasto, quindi
  niente è in Regular: corpo da Medium, titoli ed etichette da SemiBold, display serif in
  Bold. Nessuno stile scende sotto gli 11 sp, e sotto i 12 sp ci vanno solo etichette
  maiuscole brevi. Le schermate prendono un ruolo dalla scala — `Overline` per le etichette
  maiuscole di sezione, `Meta` per didascalie e ore della griglia — invece di
  ridimensionare un ruolo sulla riga.
- **Barra dei giorni.** La navigazione del giorno in agenda è un componente solo
  (`AgendaDayBar`), usato uguale dal titolare (§4.3) e dall'operatore (§3.1): frecce, data,
  "Oggi" (che compare solo quando il giorno scelto non è oggi) e la striscia dei giorni.
  Oggi porta sempre il suo punto d'oro, anche quando è il giorno selezionato: "dove sono" e
  "dov'è oggi" restano due letture distinte.
- **Navigazione in fondo.** Tutti e tre i ruoli prendono i colori da un solo helper, così
  le barre di cliente, operatore e titolare non possono divergere.

## 8. Differenze di piattaforma
- **Login social**: Android offre solo Google; iOS offre Accedi con Apple + Google (linea
  guida 4.8 dell'App Store).
- **Navigazione**: bottom bar Material, top app bar e bottom sheet su Android; tab bar
  custom, `NavigationStack` e sheet con detents su iOS.
- **Push**: FCM su Android, APNs su iOS; le due app registrano il token del dispositivo
  sullo stesso endpoint. Nessuno dei due trasporti è ancora collegato (§9): le notifiche
  arrivano nella pagina notifiche dell'app.

## 9. Punti aperti
Decisioni non ancora prese, e cose che le schermate mostrano ma il sistema non fa. Le regole
decise quando è arrivato il backend stanno al §9b, non qui.

**Da decidere**
- Pagamenti: assenti; i prezzi sono informativi.
- Impostare il canale **walk-in**: nessun form lo offre (le prenotazioni manuali vengono
  registrate come telefoniche).
- Quale servizio sia "in evidenza" (usato dalla Home per chi non ha storico) non è
  modificabile.
- Modificare un operatore esistente (orari, servizi, ferie), disattivarlo, rimuovere servizi
  od operatori: nessuna schermata lo prevede, anche se i dati lo permettono.
- I segmenti **Fedeli** e **No-show** (§6.5) esistono nei dati ma nessuna schermata ci
  filtra sopra.

**Ancora scollegato**
- **Consegna del link di recupero password** (§1.4): il link viene creato e funziona, ma non
  parte nessuna email — fuori dalla produzione viene scritto nel log del server. Le opzioni
  SMS e WhatsApp non mandano niente, e il numero mascherato mostrato è un numero demo fisso.
- **Accedi con Google / Apple**: il server non verifica ancora il token del provider, quindi
  in produzione il login social viene rifiutato ("L'accesso con Google non è ancora attivo")
  e non crea mai un account da solo.
- **Consegna delle push**: le notifiche vengono scritte e mostrate nell'app, e registrate nel
  log del server; l'invio via FCM (Android) e APNs (iOS) non è collegato.
- **Operatore in ritardo** e **Promozioni nei giorni vuoti** (§4.7): gli interruttori si
  salvano, nessuno li legge.
- L'interruttore **"Invia SMS di conferma"** (§4.4) non viene salvato con la prenotazione.
- **"Proponi altro orario"** (§3.2) non ha un flusso di proposta dietro: il conflitto viene
  solo segnato come gestito.
- Pulsanti ancora senza comportamento: "Al calendario" / "Aggiungi al calendario"
  (cliente), "Modifica" nella scheda cliente, "+ aggiungi" nella card operatore.
- La password provvisoria di un nuovo operatore (§4.6) torna una sola volta alla creazione
  dell'account: nessuna email di invito, e niente obbliga a cambiarla al primo accesso.

**Spigoli da sistemare**
- I promemoria non hanno una fascia notturna: un promemoria a 2 ore per un appuntamento
  delle 09:00 parte alle 07:00.
- "Segna completato" può ancora chiudere un appuntamento prima che sia avvenuto (il no-show
  aspetta l'orario d'inizio).
- Operatore e titolare possono trascinare un appuntamento nel passato; solo il lato cliente
  lo rifiuta.
- Le notifiche non vengono mai cancellate né archiviate.
- La scala oraria delle agende, in entrambe le app, è una finestra fissa 09:00–20:00: gli
  orari di apertura impostati fuori da lì (§4.8) non compaiono nelle agende di operatore e
  titolare.
- Le richieste in lista d'attesa vengono riesaminate solo su annullamento, spostamento o
  blocco cancellato (§6.4): un appuntamento accorciato o un turno allargato liberano un
  posto di cui nessuno viene avvisato.
- "Passa a <collega>" (§3.2) non dice niente quando il collega a quell'ora è occupato: il
  conflitto resta semplicemente irrisolto.

## 9b. Regole lato server (Fase 2)

Decise quando è arrivato il backend; `docs/API.md` è il contratto degli endpoint e
`backend/migrations/0001_init.sql` lo schema. Le app ci si stanno spostando sopra un
repository alla volta: una schermata ancora servita dai dati demo in memoria si comporta
come in Fase 1 finché il suo repository non viene sostituito, e solo allora segue le regole
qui sotto.

- **Annullamento**: il cliente può annullare un appuntamento fino a **2 ore** prima
  dell'inizio; più sotto l'app gli dice di chiamare il salone. Siccome "Modifica" sostituisce
  il vecchio appuntamento (`replacesAppointmentId`, una sola transazione), alle modifiche
  vale la stessa finestra. Operatore e titolare annullano sempre. Non si applica nessuna
  penale.
- **Stati**: un lavoro pianificato (ogni minuto) porta gli appuntamenti confermati in corso
  all'inizio e completati alla fine. A mano (`POST /appointments/:id/status`): NO_SHOW
  passato l'orario d'inizio, anche da COMPLETED — il cliente viene avvisato
  (`BOOKING_NO_SHOW`) — e COMPLETED da NO_SHOW per correggerlo. I passaggi sono controllati
  e idempotenti, quindi nessuna visita si conta due volte.
- **I contatori del cliente** (visite, spesa totale, no-show, ultima visita) si ricavano
  dagli appuntamenti, non si salvano: annullare o correggere un appuntamento rimette a posto
  le statistiche.
- **Slot, disponibilità, prima disponibilità, posizioni in coda, statistiche del cliente, KPI
  e copertura delle campagne li calcola il server.** Le app li mostrano.
- **Concorrenza**: due appuntamenti sovrapposti sullo stesso operatore sono impossibili — li
  rifiuta il database. Il cliente che conferma un orario appena preso da un altro riceve
  `SLOT_NO_LONGER_AVAILABLE`.
- **Lista d'attesa**: una richiesta per cliente, giorno e scelta di operatore. Quando si
  libera un posto (annullamento, spostamento, blocco rimosso) viene avvisata esattamente una
  persona — la prima in coda i cui servizi ci stanno — rispettando il suo interruttore "Slot
  liberi"; il posto non viene trattenuto. Le richieste scadono quando il giorno passa, quelle
  avvisate dopo 24 ore, e niente le riapre.
- **Promemoria**: una notifica per appuntamento e per regola, solo se il cliente ha acceso
  "Promemoria appuntamento", e mai per una prenotazione fatta quando l'ora del promemoria era
  già passata.
- **Campagne**: destinatari = consenso marketing sulla scheda cliente + un account collegato
  con "Promozioni e novità" acceso. L'invio è messo in coda, mai fatto al volo; il tetto agli
  invii è cumulativo e la ripetizione settimanale si ferma dopo 4 esecuzioni.
- **Dati economici**: per il ruolo STAFF il server non seleziona nemmeno gli importi — il
  titolare è l'unico ruolo che riceve i campi con il denaro.
- **Ambito**: il cliente legge solo le proprie righe; l'operatore solo la propria agenda, i
  propri blocchi e le proprie ferie — chiedere quelle di un collega viene rifiutato, non
  ignorato in silenzio; il titolare vede tutto il salone ed è l'unico che può spostare un
  appuntamento su un altro operatore o raggiungere gli endpoint di Gestione.
- **Tempo**: il salone lavora in `Europe/Rome`; gli istanti sono salvati in UTC.
- **Sessioni**: token di accesso 15 minuti, refresh 30 giorni, ruotato a ogni uso; riusare un
  refresh revocato chiude tutte le sessioni di quell'account, mentre un logout normale chiude
  solo quel dispositivo. Cambiare o reimpostare la password le chiude tutte. I link di reset
  durano 30 minuti e funzionano una volta sola.
- **Validazioni**: le regole stanno in `backend/src/lib/validation.ts` e le due app le
  ripetono (telefono normalizzato in E.164 con +39 di default, password di almeno 8 caratteri
  con una lettera e una cifra, nota da 200 caratteri, durate a passi di 5 minuti, prezzi in
  centesimi).

## 10. Modello di dominio

Le entità così come le schermate sopra le creano e le leggono, e come
`backend/migrations/0001_init.sql` le salva. Gli identificativi sono UUID, il denaro è sempre
in centesimi interi, gli istanti sono salvati in UTC e mostrati nell'orario del salone
(`Europe/Rome`). I valori *derivati* si calcolano da altri dati, non si inseriscono mai a
mano.

| Entità | Campi | Note |
|---|---|---|
| **User** (account) | id, ruolo `CLIENT`/`STAFF`/`OWNER`, nome, cognome, email (unica, login), telefono, hash password, foto (operatore/titolare), data di disattivazione, data di creazione | Un login per tutti i ruoli. Un account CLIENT punta a una scheda cliente, uno STAFF/OWNER a un operatore; mai a entrambi. Le identità social (Google; Apple su iOS) fanno accedere un account che esiste già, non ne creano (§9). |
| **Client** | id, nome, cognome, telefono (unico, E.164), email (facoltativa), user id (facoltativo), data di creazione ("cliente dal") | Un cliente registrato dall'app ha uno User; uno creato dalla prenotazione manuale no. *Derivati* dagli appuntamenti: visite, spesa totale, numero di no-show, ultima visita, operatore preferito, giorni medi tra le visite. |
| **ClientNotificationPrefs** | user id, promemoria appuntamento, avvisi lista d'attesa, marketing | Default ON/ON/OFF. Marketing = la metà lato cliente del consenso alle campagne (§6.5). |
| **RefreshToken** (sessione) | id, user id, hash del token, scadenza, revocato il, sostituito da, user agent, data di creazione | Una riga per dispositivo collegato; la rotazione lega ogni riga alla successiva (§9b). |
| **PasswordResetToken** | user id, hash del token, scadenza (30 min), usato il | Monouso. |
| **DeviceToken** | id, user id, piattaforma `ANDROID`/`IOS`, token (unico), ultimo utilizzo | Registrato all'accesso, rimosso al logout. |
| **Salon** | nome, indirizzo, città, telefono, orari di apertura | Riga unica. Orari: per giorno della settimana una o più fasce (turno spezzato); nessuna fascia = chiuso. |
| **Operator** | id, nome visualizzato, mansione, bio, specialità, è titolare, attivo | Il collegamento all'account sta sullo User (`operator id`). Anche la foto sta sullo User. |
| **OperatorWorkingHours** | operator id, giorno della settimana, inizio, fine | Più fasce al giorno ammesse (es. pausa pranzo); nessuna = giorno libero. Sempre intersecate con gli orari del salone. |
| **Service** | id, nome, descrizione, durata (5–240 min), prezzo (centesimi), in evidenza, attivo | Lista piatta, senza categorie. I servizi si disattivano, non si cancellano. |
| **OperatorService** | operator id, service id | Abilitazione (N:N). |
| **Appointment** | id, client id, operator id, inizio, fine, durata, prezzo totale, stato, canale `APP`/`PHONE`/`WALK_IN`, nota per l'operatore (≤ 200), annullato da `CLIENT`/`SALON`, annullato il, completato il, creato da, data di creazione | Stato `CONFIRMED`/`IN_PROGRESS`/`COMPLETED`/`CANCELLED`/`NO_SHOW`. Fine = inizio + durata. Due appuntamenti attivi dello stesso operatore non possono mai sovrapporsi: lo rifiuta il database. |
| **AppointmentService** | appointment id, service id, posizione, nome, prezzo, durata | Nome, prezzo e durata copiati al momento della prenotazione, così lo storico sopravvive a un cambio di listino. |
| **TimeBlock** | id, operator id, motivo `PERMESSO`/`PAUSA`/`FERIE`/`CORSO`, data, inizio, fine, etichetta (facoltativa) | Uno per giorno, con una fascia oraria dentro (§3.2). |
| **Holiday** | id, operator id, data di inizio, data di fine (inclusa), etichetta | Giornate intere di assenza, entità a sé; due periodi dello stesso operatore non possono sovrapporsi. Le ferie svuotano il giorno per le prenotazioni e restano fuori dal calcolo dell'occupazione (§4.1). |
| **WaitlistEntry** | id, client id, data, ora (facoltativa = qualsiasi), operator id (facoltativo = qualsiasi), servizi, durata, prezzo totale, stato `WAITING`/`NOTIFIED`/`EXPIRED`, data di creazione, avvisato il, scade il | Una richiesta in attesa per cliente, giorno e scelta di operatore. *Derivata*: posizione in coda (§6.4). |
| **ReminderRule** | id, ore prima (1–168, unica) | A livello di salone; l'app propone 24 o 48 ore per il primo e 2 o 4 ore per i successivi. L'ordine sono le ore prima, non c'è una posizione a parte. |
| **NotificationSettings** | conferma prenotazione, annullamento, operatore in ritardo, promozioni nei giorni vuoti | Interruttori di salone (§4.7); gli ultimi due non li legge ancora nessuno (§9). |
| **PushCampaign** | id, nome, segmento `TUTTI`/`INATTIVI_60`/`TOP_SPESA`, titolo, testo (≤ 140, token `{{nome}}` `{{link}}`), programmata per (nessuna = invio subito), ripeti ogni settimana, tetto agli invii, stato `DRAFT`/`SCHEDULED`/`SENT`, copertura e dimensione del segmento salvate all'invio | Una campagna già inviata è in sola lettura. |
| **CampaignSend** | campaign id, client id, inviata il | Una riga per ogni cliente davvero raggiunto: è ciò che limita gli invii ed evita che un cliente senta la stessa campagna due volte in un giorno. |
| **Notification** | id, user id, tipo, titolo, testo, payload, data di creazione, letta il | La lista in app (§5); il testo lo scrive il server. Tipi: `BOOKING_CONFIRMED`, `BOOKING_REMINDER`, `BOOKING_CANCELLED`, `BOOKING_RESCHEDULED`, `BOOKING_NO_SHOW`, `WAITLIST_SLOT`, `CAMPAIGN`, `GENERIC`. |
