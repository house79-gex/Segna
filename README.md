# Segna - Sistema di Comunicazione Multi-Dispositivo

Sistema professionale di comunicazione in tempo reale tra smartphone, smartwatch, ESP32 e dispositivi Android receiver.

## 🆕 Aggiornamento Versione 3.1 - Android Receiver App

### 📱 Nuova App: Android Receiver

È stata aggiunta una nuova app Android nativa (`android_receiver_app/`) che permette di usare un **secondo smartphone Android** come dispositivo ricevitore con vibrazione. Funziona anche con **schermo spento** grazie a:

#### ✨ Caratteristiche Principali
- **Foreground Service**: Rimane attivo in background con notifica persistente
- **WakeLock PARTIAL**: CPU attiva, schermo può dormire
- **HTTP Server**: Porta 5001 (diversa dal Wear OS watch)
- **Pattern vibrazione**: Identici al watch (numeric/melodic)
- **Auto IP detection**: Rileva automaticamente IP locale
- **UI minima**: Start/Stop server + visualizzazione IP

#### 🔧 API Endpoints
- `GET /status` - Verifica stato server
- `POST /command` - Invia comandi di vibrazione (compatibile con app controller)

#### 📖 Documentazione
Vedi [android_receiver_app/README.md](./android_receiver_app/README.md) per istruzioni di build, installazione e integrazione con l'app controller Flutter.

---

## 🆕 Aggiornamento Versione 3.0 - Wear OS Data Layer

### 🎯 Risoluzione Conflitti Samsung Wearable

**PROBLEMA RISOLTO**: L'app ora utilizza **Wear OS Data Layer API** invece di BLE diretto per la comunicazione con il watch, eliminando completamente i conflitti con Samsung Wearable.

#### ✅ Vantaggi della Nuova Architettura
- **Nessun conflitto Bluetooth**: Usa la connessione Wear OS esistente
- **Watch sempre attivo**: Wakelock mantiene l'app in esecuzione
- **Chiusura remota**: Controllo completo dell'app watch dallo smartphone
- **Più affidabile**: API ufficiali Google invece di BLE custom
- **Compatibilità totale**: Funziona con tutti i device Wear OS

### 🏗️ Architettura Completa

```
┌──────────────────────────────────────────────────────────────────┐
│                    SMARTPHONE (Flutter)                           │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  UI: 5 Pulsanti + RESET + Chiudi App Watch                 │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                          │                                        │
│              ┌───────────┼────────────┐                          │
│              │           │            │                           │
│      Wear OS Message API │        BLE Direct                     │
│     (Platform Channel)   │     (flutter_blue_plus)               │
└──────────────┼────────────┼────────────┼──────────────────────────┘
               │            │            │
      ┌────────▼────┐  ┌───▼────┐  ┌───▼────────┐
      │ SMARTWATCH  │  │ ANDROID│  │   ESP32     │
      │  (Wear OS)  │  │RECEIVER│  │(BLE Server) │
      ├─────────────┤  ├────────┤  ├─────────────┤
      │MessageClient│  │  HTTP  │  │ 5 LED       │
      │+ Wakelock   │  │ Server │  │ Singoli     │
      │+ No Back Btn│  │:5001   │  │ GPIO 25-33  │
      └─────────────┘  └────────┘  └─────────────┘
      Vibrazione       Vibrazione   LED Visivi
      (Message API)    (HTTP/WiFi)  (BLE)

Wear OS: Message API via /segna_channel
Android Receiver: HTTP Server su porta 5001
ESP32: BLE UUID 4fafc201-1fb5-459e-8fcc-c5c9c331914b
```

### 🆕 Nuove Funzionalità Principali

#### 📱 Smartphone App
- **Pulsante "Chiudi App Watch"**: Chiude l'app watch da remoto
- **Connessione automatica**: Si connette al watch via Wear OS all'avvio
- **Indicatore Wear OS**: Mostra "Watch (Wear OS)" invece di "Watch"
- **Approccio ibrido**: Wear OS per watch, BLE per ESP32

#### ⌚ Watch App  
- **App sempre attiva**: Wakelock impedisce la chiusura automatica
- **Back button disabilitato**: Previene chiusura accidentale
- **Chiusura remota**: Solo lo smartphone può chiudere l'app
- **Nessun BLE**: Comunicazione tramite Wear OS Message API

#### 📡 Comunicazione
- **Wear OS Message API**: Comunicazione watch ↔ smartphone
- **BLE per ESP32**: Invariato, funziona come prima
- **Nessun conflitto**: Samsung Wearable può rimanere sempre connesso

---

## 🎨 Versione 2.0 - Funzionalità Precedenti

### ⚙️ Menu Configurazioni Watch
L'app smartwatch include un menu completo di configurazioni:

**Modalità Visualizzazione:**
- **Lettera e Colore** (default): Mostra sia la lettera che il colore di sfondo
- **Solo Lettera**: Mostra solo la lettera su sfondo nero
- **Solo Colore**: Mostra solo il colore (senza testo)

**Dimensione Lettera:**
- **Grande** (200sp): Lettera a schermo intero
- **Media** (120sp): Dimensione standard
- **Piccola** (60sp): Lettera compatta

**Dimensione Colore:**
- **Schermo intero**: Colore occupa tutto lo schermo
- **Cerchio Grande**: Cerchio colorato da 300dp
- **Cerchio Medio**: Cerchio colorato da 200dp
- **Cerchio Piccolo**: Punto colorato da 100dp

### 🔐 Build Firmato per Produzione
- Keystore di produzione configurato automaticamente
- APK firmato pronto per installazione su qualsiasi dispositivo
- Firma automatica per build debug e release

---

## 📋 Descrizione

Segna è un'applicazione distribuita che permette il controllo sincronizzato di dispositivi multipli. Il sistema è composto da:

- **App Smartphone (Flutter)**: Interfaccia di controllo principale con 5 pulsanti colorati, impostazioni configurabili e chiusura remota watch
- **App Smartwatch (Wear OS)**: Display visuale o modalità vibrazione, sempre attiva con wakelock
- **App Android Receiver**: Secondo smartphone che riceve comandi HTTP e vibra (funziona con schermo spento)
- **Firmware ESP32**: Controllore hardware per 5 LED singoli separati

### ✨ Funzionalità Complete

#### Smartphone
- Layout UI con pulsanti in colonna centrale verticale
- Pulsante Reset compatto in basso a destra
- **Pulsante "Chiudi App Watch"** per chiusura remota
- Schermata impostazioni completa per configurare Watch e ESP32
- Connessione automatica tramite Wear OS e scansione BLE

#### Smartwatch
- Modalità vibrazione: schermo nero con vibrazioni (1-5 volte in base alla lettera)
- Modalità display: lettera a schermo intero con colore di sfondo
- **App sempre attiva**: Wakelock + back button disabilitato
- **Chiusura solo da smartphone**: Massimo controllo
- Comunicazione tramite Wear OS Message API

#### Android Receiver (Nuovo!)
- **HTTP Server**: Porta 5001, compatibile con app controller
- **Vibrazione pattern**: Numeric/Melodic identici al watch
- **Foreground Service**: Rimane attivo con notifica persistente
- **WakeLock PARTIAL**: Funziona con schermo spento
- **UI minima**: Start/Stop server + visualizzazione IP

#### ESP32
- 5 LED singoli separati (uno per colore) invece di LED RGB
- LED sempre acceso o temporizzato (configurabile)
- Funzione lampeggio di tutti i LED per reset/avvio
- Invio conferme BLE allo smartphone

## 🎨 Mappatura Colori

| Lettera | Colore | Codice Hex | RGB           |
|---------|--------|------------|---------------|
| A       | Bianco | #FFFFFF    | (255,255,255) |
| B       | Giallo | #FFFF00    | (255,255,0)   |
| C       | Verde  | #00FF00    | (0,255,0)     |
| D       | Rosso  | #FF0000    | (255,0,0)     |
| E       | Blu    | #0000FF    | (0,0,255)     |
| RESET   | Nero   | #000000    | (0,0,0)       |

## 📁 Struttura del Progetto

```
Segna/
├── flutter_app/
│   ├── pubspec.yaml                    # Dipendenze Flutter
│   ├── lib/
│   │   ├── main.dart                   # App principale smartphone (Wear OS + BLE)
│   │   ├── settings_page.dart          # Schermata impostazioni
│   │   └── models/
│   │       └── settings_model.dart     # Modello dati impostazioni
│   └── android/
│       └── app/
│           ├── build.gradle            # Dipendenza Wear OS
│           └── src/
│               └── main/
│                   ├── kotlin/         # Platform channel Wear OS
│                   └── AndroidManifest.xml
├── wear_os_app/
│   └── app/
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml  # Permessi wakelock
│               └── java/
│                   └── com/
│                       └── example/
│                           └── watchreceiver/
│                               └── MainActivity.kt  # App smartwatch (Wear OS Message API)
├── android_receiver_app/                # 🆕 NUOVO: Android Receiver
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/example/segnareceiver/
│   │   │   │   ├── MainActivity.kt      # UI Start/Stop server
│   │   │   │   ├── ReceiverService.kt   # Foreground service con WakeLock
│   │   │   │   ├── AndroidServer.kt     # HTTP server (NanoHTTPD)
│   │   │   │   └── VibrationHandler.kt  # Pattern vibrazione
│   │   │   ├── res/                     # Layout e risorse
│   │   │   └── AndroidManifest.xml      # Permessi
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── README.md                        # 📖 Documentazione Android Receiver
├── esp32_firmware/
│   └── esp32_led_controller.ino        # Firmware ESP32 (invariato)
├── WEAR_OS_MIGRATION_GUIDE.md          # 📖 Guida completa migrazione Wear OS
├── SAP_INTEGRATION_GUIDE.md            # Guida SAP (legacy)
└── README.md                            # Questo file
```

## 📖 Documentazione

- **[android_receiver_app/README.md](./android_receiver_app/README.md)** - Guida completa all'Android Receiver: build, installazione, API e integrazione
- **[WEAR_OS_MIGRATION_GUIDE.md](./WEAR_OS_MIGRATION_GUIDE.md)** - Guida completa alla migrazione da BLE a Wear OS Data Layer, troubleshooting e best practices
- **[SAP_INTEGRATION_GUIDE.md](./SAP_INTEGRATION_GUIDE.md)** - Documentazione legacy su Samsung Accessory Protocol (non necessario con Wear OS)
├── esp32_firmware/
│   └── esp32_led_controller.ino        # Firmware ESP32
└── README.md                            # Questo file
```

## 🚀 Installazione

### 1. App Smartphone (Flutter)

#### Prerequisiti
- Flutter SDK >= 3.0.0
- Android Studio o VS Code con plugin Flutter
- Smartphone Android con BLE (es. Samsung Galaxy S23 Ultra)

#### Procedura
```bash
# Naviga nella cartella flutter_app
cd flutter_app

# Installa le dipendenze
flutter pub get

# Connetti il dispositivo Android
# Abilita Debug USB sul telefono

# Verifica la connessione
flutter devices

# Compila e installa l'app
flutter run
```

#### Permessi Android
L'app richiede i seguenti permessi (già configurati in AndroidManifest.xml):
- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

### 2. App Smartwatch (Wear OS)

#### Prerequisiti
- Android Studio
- Smartwatch Wear OS (es. Samsung Galaxy Watch 5)
- SDK Android con supporto Wear OS

#### Procedura
```bash
# Apri il progetto wear_os_app in Android Studio

# Nel file build.gradle (app level), aggiungi:
dependencies {
    implementation 'androidx.compose.ui:ui:1.5.0'
    implementation 'androidx.compose.material3:material3:1.1.0'
    implementation 'androidx.activity:activity-compose:1.7.0'
}

# Connetti lo smartwatch via Wi-Fi debugging o Bluetooth
# Abilita Developer Options sul watch

# Compila e installa
./gradlew installDebug

# Oppure usa Android Studio: Run > Run 'app'
```

### 3. Firmware ESP32

#### Prerequisiti
- ESP32 DevKit (qualsiasi modello con supporto BLE)
- Arduino IDE >= 2.0
- 5 LED singoli (1 bianco, 1 giallo, 1 verde, 1 rosso, 1 blu)
- 5 resistenze da 220Ω

#### Installazione IDE e Librerie
```bash
# 1. Installa Arduino IDE da: https://www.arduino.cc/en/software

# 2. Aggiungi supporto ESP32:
#    File > Preferences > Additional Board Manager URLs:
#    https://dl.espressif.com/dl/package_esp32_index.json

# 3. Installa ESP32 board:
#    Tools > Board > Boards Manager > cerca "ESP32" > Install

# 4. Installa librerie:
#    Tools > Manage Libraries...
#    - Cerca "ArduinoJson" > Install (by Benoit Blanchon)
#    - ESP32 BLE Arduino è già incluso nel core ESP32
```

#### Schema Collegamenti Hardware

**5 LED Singoli:**
```
ESP32          LED
GPIO 25  ----> LED Bianco (Lettera A) + Resistenza 220Ω ----> GND
GPIO 26  ----> LED Giallo (Lettera B) + Resistenza 220Ω ----> GND
GPIO 27  ----> LED Verde  (Lettera C) + Resistenza 220Ω ----> GND
GPIO 32  ----> LED Rosso  (Lettera D) + Resistenza 220Ω ----> GND
GPIO 33  ----> LED Blu    (Lettera E) + Resistenza 220Ω ----> GND

Note: Ogni LED ha la sua resistenza in serie da 220Ω
```

**Diagramma:**
```
                    ┌─────────┐
     GPIO 25 ──────┤ 220Ω    ├──── LED Bianco ──┐
                   └─────────┘                   │
                    ┌─────────┐                  │
     GPIO 26 ──────┤ 220Ω    ├──── LED Giallo ──┤
                   └─────────┘                   │
                    ┌─────────┐                  ├──── GND
     GPIO 27 ──────┤ 220Ω    ├──── LED Verde  ──┤
                   └─────────┘                   │
                    ┌─────────┐                  │
     GPIO 32 ──────┤ 220Ω    ├──── LED Rosso  ──┤
                   └─────────┘                   │
                    ┌─────────┐                  │
     GPIO 33 ──────┤ 220Ω    ├──── LED Blu    ──┘
                   └─────────┘
```

#### Upload Firmware
```bash
# 1. Apri esp32_led_controller.ino in Arduino IDE

# 2. Configura la board:
#    Tools > Board > ESP32 Arduino > ESP32 Dev Module

# 3. Seleziona la porta:
#    Tools > Port > (seleziona la porta COM/USB dell'ESP32)

# 4. Upload:
#    Sketch > Upload (o Ctrl+U)

# 5. Apri Serial Monitor per debug:
#    Tools > Serial Monitor (115200 baud)
```

## 🎮 Utilizzo

### Avvio del Sistema

1. **Accendi l'ESP32**
   - I LED faranno un test sequenziale (Bianco > Giallo > Verde > Rosso > Blu)
   - Sul Serial Monitor vedrai: "BLE Server pronto!"

2. **Apri l'App Smartwatch**
   - Sarà visibile come dispositivo BLE "Galaxy Watch 5"

3. **Apri l'App Smartphone**
   - Tocca l'icona Bluetooth in alto a destra per scansionare dispositivi BLE
   - L'app si connetterà automaticamente a "ESP32-Segna" e "Galaxy Watch"
   - Gli indicatori di stato diventeranno verdi

4. **Configura le Impostazioni (opzionale)**
   - Tocca l'icona ingranaggio (⚙️) in alto a sinistra
   - Configura le opzioni per Smartwatch (vibrazione) e ESP32 (LED)
   - Salva le impostazioni

### Invio Comandi

1. **Tocca un pulsante lettera (A, B, C, D, E)**
   - Se modalità display: lo smartwatch mostrerà la lettera a schermo intero con sfondo colorato
   - Se modalità vibrazione: lo smartwatch vibrerà 1-5 volte in base alla lettera
   - Il LED ESP32 corrispondente si accenderà
   - Gli indicatori dello smartphone mostreranno un bordo colorato quando i dispositivi confermano

2. **Tocca RESET (in basso a destra)**
   - Lo smartwatch vibrerà (se abilitato) e tornerà allo stato iniziale
   - L'ESP32 lampeggerà tutti i LED (se abilitato) e li spegnerà
   - Gli indicatori dello smartphone perderanno il bordo colorato

### Esempio di Payload BLE

**Comando Lettera:**
```json
{
  "letter": "A",
  "color": "#FFFFFF",
  "colorName": "WHITE",
  "settings": {
    "watch": {
      "vibrationMode": true,
      "vibrationDuration": 300,
      "vibrationPause": 200
    },
    "esp32": {
      "alwaysOn": true,
      "duration": 3000,
      "blinkAlert": false,
      "blinkCount": 3,
      "blinkDuration": 200
    }
  }
}
```

**Comando Reset:**
```json
{
  "command": "RESET",
  "settings": {
    "watch": {
      "vibrationEnabled": true,
      "vibrationDuration": 800
    },
    "esp32": {
      "blinkAlert": true,
      "blinkCount": 3,
      "blinkDuration": 200
    }
  }
}
```

**Conferma Ricezione:**
```json
{
  "status": "received",
  "device": "esp32",
  "color": "#FFFFFF"
}
```

## 🔧 Troubleshooting

### App Smartphone

**Problema: "Errore durante la scansione"**
- Verifica che i permessi Bluetooth siano concessi nelle impostazioni Android
- Verifica che il Bluetooth sia attivo
- Verifica che la localizzazione sia attiva (richiesta per BLE scan su Android)

**Problema: Dispositivi non trovati**
- Assicurati che ESP32 sia acceso e abbia completato l'inizializzazione
- Assicurati che lo smartwatch abbia l'app attiva
- Riavvia la scansione

**Problema: "Errore connessione"**
- Verifica che il dispositivo non sia già connesso ad altro
- Disconnetti e riconnetti
- Riavvia l'app

### App Smartwatch

**Problema: Schermo nero sempre**
- Verifica che l'app sia in esecuzione (non in background)
- Controlla i log Android tramite `adb logcat`
- Riavvia l'app

**Problema: Colori non corretti**
- Verifica il formato del JSON ricevuto
- Controlla il parsing dei colori hex

### ESP32

**Problema: LED non si accende**
- Verifica i collegamenti hardware con un multimetro
- Controlla la polarità del LED RGB (catodo comune vs anodo comune)
- Verifica le resistenze in serie (220Ω raccomandate)
- Testa manualmente i pin con `ledcWrite()`

**Problema: BLE non funziona**
- Verifica nel Serial Monitor se il server BLE è avviato
- Riavvia l'ESP32
- Controlla che gli UUID nel codice siano corretti
- Verifica che la libreria ESP32 BLE sia installata correttamente

**Problema: JSON parsing error**
- Verifica il formato del JSON inviato
- Controlla la dimensione del buffer in `StaticJsonDocument<256>`
- Abilita debug con `Serial.println()` per vedere i dati ricevuti

**Problema: LED sempre al massimo o sfarfallante**
- Per LED catodo comune: usa `ledcWrite(channel, value)` direttamente
- Per LED anodo comune: usa `ledcWrite(channel, 255 - value)` (invertito)
- Verifica la frequenza PWM (5000 Hz è standard)

### Debug Avanzato

**ESP32 Serial Monitor:**
```bash
# Visualizza tutti i log
Tools > Serial Monitor (115200 baud)

# Log attesi:
Avvio ESP32 BLE LED Controller...
Test LED...
BLE Server pronto!
In attesa di connessioni...
Client connesso
Ricevuto comando: {"letter":"A","color":"#FFFFFF","colorName":"WHITE"}
Lettera: A - Colore: WHITE
LED impostato su RGB(255, 255, 255)
```

**Android Logcat per Watch:**
```bash
adb logcat | grep watchreceiver
```

**Flutter Debug:**
```bash
flutter logs
```

## 📚 Dipendenze

### Flutter
- `flutter_blue_plus: ^1.31.0` - Gestione BLE
- `permission_handler: ^11.0.1` - Gestione permessi Android
- `shared_preferences: ^2.2.2` - Persistenza impostazioni

### ESP32
- `ArduinoJson` - Parsing JSON
- `ESP32 BLE Arduino` - Stack BLE (incluso nel core)

### Wear OS
- `androidx.compose.ui` - UI Compose
- `androidx.compose.material3` - Material Design 3
- `androidx.activity:activity-compose` - Activity per Compose

## 🔐 Specifiche BLE

- **Service UUID**: `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Characteristic UUID**: `beb5483e-36e1-4688-b7f5-ea07361b26a8`
- **Properties**: READ, WRITE, NOTIFY
- **Max Payload**: 512 bytes
- **Encoding**: UTF-8

## 👥 Dispositivi Testati

- ✅ Samsung Galaxy S23 Ultra (Android 14, UI 8)
- ✅ Samsung Galaxy Watch 6 (Wear OS 5.0, UI 6, Android 14)
- ✅ Samsung Galaxy Watch 5 (Wear OS 3.5)
- ✅ ESP32 DevKit V1 (30 pin)
- ✅ ESP32 WROOM-32 (38 pin)

## 📦 Build Release APK

Per compilare l'APK firmato della watch app:

```bash
cd wear_os_app
./gradlew assembleRelease

# L'APK firmato si trova in:
# app/build/outputs/apk/release/app-release.apk
```

### Installazione APK su Watch

1. **Abilita ADB sul Watch**:
   - Vai in Impostazioni > Info orologio
   - Tocca 7 volte su "Numero build"
   - Torna indietro e apri "Opzioni sviluppatore"
   - Attiva "Debug ADB" e "Debug via Wi-Fi"

2. **Connetti il Watch via ADB**:
   ```bash
   # Trova l'indirizzo IP del watch (visibile in Debug via Wi-Fi)
   adb connect <IP_WATCH>:5555
   
   # Verifica connessione
   adb devices
   ```

3. **Installa l'APK**:
   ```bash
   adb install wear_os_app/app/build/outputs/apk/release/app-release.apk
   ```

### Note sulla Firma

- **Keystore**: `wear_os_app/keystore/release.keystore`
- **Password**: segna2026
- **Alias**: segna_key
- Il keystore NON è committato nel repository per sicurezza
- Per generarlo nuovamente, vedi la sezione Build nel SAP_INTEGRATION_GUIDE.md

## 📝 Note Tecniche

- Il codice è scritto seguendo le best practices per codice pulito e manutenibile
- Nomi di variabili e funzioni sono descrittivi e in italiano dove appropriato
- Il sistema supporta connessioni multiple simultanee
- La comunicazione è bidirezionale: smartphone ↔ watch/ESP32
- Feedback visivo con conferme BLE dai dispositivi riceventi
- Le impostazioni sono persistenti tramite SharedPreferences
- JSON payload esteso per includere configurazioni device-specific

## ✅ Funzionalità Implementate

- [x] 5 LED singoli separati su ESP32
- [x] Modalità vibrazione su Smartwatch
- [x] Feedback di stato dai dispositivi riceventi
- [x] Sistema di impostazioni configurabile
- [x] Nuovo layout UI ottimizzato
- [x] Connessione automatica ai dispositivi

## 🔮 Prossimi Sviluppi

- [ ] Salvataggio sequenze di comandi
- [ ] Modalità automatica con pattern predefiniti
- [ ] Supporto iOS
- [ ] Gestione profili multipli

## 📄 Licenza

Questo progetto è rilasciato sotto licenza MIT.

## 🤝 Contributi

I contributi sono benvenuti! Per favore:
1. Fai un fork del progetto
2. Crea un branch per la tua feature (`git checkout -b feature/AmazingFeature`)
3. Commit le tue modifiche (`git commit -m 'Add some AmazingFeature'`)
4. Push al branch (`git push origin feature/AmazingFeature`)
5. Apri una Pull Request

## 📞 Supporto

Per problemi o domande, apri una issue su GitHub.
