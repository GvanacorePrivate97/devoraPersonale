# Men Care — iOS

App SwiftUI nativa (iOS 17+), clone 1:1 dell'app Android in `../android/`: stessi
ruoli, stesse schermate, stesse regole di business, stesso design system e stessi
dati demo. La specifica funzionale e il riferimento visivo sono condivisi:
`../docs/FEATURES.it.md` e `../design/mockup.dc.html`.

Le due app parlano con lo stesso backend (`../backend/`, Fastify + PostgreSQL);
il contratto degli endpoint è `../docs/API.md` e le regole che decide il server
sono il §9b di `../docs/FEATURES.it.md`. Il passaggio dai repository fake a
quelli di rete è in corso: finché un repository non è stato sostituito, l'app
gira sul data layer in memoria (`Core/Data/Fake` + `DemoSeed`) e non serve
nessun server per aprirla.

## Build (richiede macOS)

Il progetto usa [XcodeGen](https://github.com/yonaskolb/XcodeGen); il file
`.xcodeproj` non è versionato.

```bash
brew install xcodegen   # una tantum
cd ios
xcodegen generate
open MenCare.xcodeproj
```

Poi ⌘R su un simulatore iPhone (iOS 17+). Test: ⌘U (target `MenCareTests`:
port di `SlotEngineTest`, `FakeBookingRepositoryTest` e `ClientHomeViewModelTest`
Android, più sanity test sui tipi data/ora).

Verificato su Xcode 26.6 con simulatore iOS 26.5 e su build per dispositivo.

### Se la build non parte

- **"No simulator runtime version … available to use with iphonesimulator SDK"**:
  il runtime del simulatore è più vecchio dell'SDK di Xcode. Scaricare quello
  abbinato da *Xcode → Settings → Components*.
- **"resource fork, Finder information, or similar detritus not allowed"** in
  fase di firma: il repo sta in una cartella sincronizzata da iCloud Drive
  (Scrivania/Documenti), che aggiunge attributi estesi ai file. Xcode compila in
  `~/Library/Developer/Xcode/DerivedData` e non ne soffre; da riga di comando
  passare `-derivedDataPath` fuori dal repo, o spostare il repo fuori da iCloud.
- **Font di sistema al posto di Cormorant/Jost**: `UIAppFonts` in `project.yml`
  va scritto coi soli nomi file, perché il build copia i font nella radice del
  bundle senza la cartella `Fonts/`.
- **Immagini che non compaiono**: `Image("nome")` cerca solo nell'asset catalog;
  i PNG vanno in `Resources/Assets.xcassets`, non sciolti in `Resources/`.

## Account demo

Con il backend avviato la password è la stessa per tutti: `mencare2026`.
Sulle schermate ancora servite dai dati fake va bene qualsiasi stringa di 6+
caratteri.

| Ruolo | Email |
|---|---|
| Cliente | `marco.esposito@gmail.com` |
| Operatore | `luca.ferrante@mencare.it` |
| Titolare | `antonio@mencare.it` |

## Struttura

Rispecchia i moduli Gradle Android come gruppi di cartelle in un unico target:

```
MenCare/
  App/                  — @main, role routing, AppContainer (DI via Environment)
  Core/Common/          — CoreTime (LocalDate/LocalTime/…), AppResult, formatter italiani, L()
  Core/Model/           — modelli di dominio (puri, nessuna dipendenza UI)
  Core/Data/            — protocolli repository, fake + DemoSeed, SlotEngine (calcolo
                          locale degli slot, finché la disponibilità non arriva dall'API)
  Core/DesignSystem/    — tema (colori/tipografia) e componenti brand
  Core/UI/              — schermate condivise tra ruoli (clienti, notifiche)
  Features/Auth|Client|Staff|Admin/
  Resources/            — font, Localizable.strings (it), Assets.xcassets (loghi, icone)
MenCareTests/           — XCTest, port dei test Android
```

Regole architetturali identiche ad Android: le feature dipendono solo da `Core/*`,
ogni accesso ai dati passa dai protocolli repository (è lì che si innesta il
backend, un repository alla volta, senza toccare le schermate), le ViewModel
sono `@MainActor @Observable`.

### Font

`Resources/Fonts/` contiene i TTF variabili di Cormorant Garamond e Jost
(gli stessi di Android). `Typo` li referenzia per nome di famiglia
("Cormorant Garamond", "Jost") e sceglie il peso con `.weight(_:)`, che sui
font variabili agisce sull'asse `wght`.

## Differenze di piattaforma

Dove iOS diverge dall'app Android:

- **Login social**: Apple + Google. "Sign in with Apple" è esposto solo qui —
  l'app Android offre il solo Google. La linea guida 4.8 dell'App Store lo
  richiede quando l'app offre un login social di terze parti. Il contratto dati
  lo prevede già: `SocialProvider.APPLE` esiste su entrambe le piattaforme.
- **Push**: APNs, dove Android usa FCM. Il token del dispositivo si registra con
  `POST /v1/auth/me/devices` indicando `platform: IOS`.
- **Navigazione**: `NavigationStack` + tab bar custom al posto di Compose
  Navigation + Material bottom bar; sheet con `presentationDetents` al posto
  dei bottom sheet Material.

Per il resto — ruoli, wizard di prenotazione, regole di disponibilità, KPI —
vale `../docs/FEATURES.it.md` senza differenze.
