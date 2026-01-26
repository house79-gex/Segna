# Segna Receiver - Android App

## 📱 Descrizione

App Android nativa che riceve comandi HTTP e vibra in base ai pattern configurati. Funziona su smartphone Android standard con schermo spento grazie a:

- **Foreground Service** con notifica persistente
- **WakeLock PARTIAL** (CPU attiva, schermo può dormire)
- **HTTP Server** sulla porta 5001
- **Pattern vibrazione** identici al Wear OS watch (numeric/melodic)

## 🏗️ Struttura

```
android_receiver_app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/segnareceiver/
│   │   │   ├── MainActivity.kt          # UI per start/stop server
│   │   │   ├── ReceiverService.kt       # Foreground service con WakeLock
│   │   │   ├── AndroidServer.kt         # HTTP server (NanoHTTPD)
│   │   │   └── VibrationHandler.kt      # Gestione pattern vibrazione
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   └── values/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 🚀 Build & Install

### Prerequisiti
- Android Studio Arctic Fox o superiore
- JDK 8 o superiore
- Android SDK 26+ (Android 8.0 Oreo)

### Build

```bash
cd android_receiver_app
./gradlew assembleDebug
```

L'APK verrà generato in: `app/build/outputs/apk/debug/app-debug.apk`

### Install

```bash
# Via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Oppure copia l'APK sul dispositivo e installa manualmente
```

## 📡 Utilizzo

1. **Apri l'app** sul dispositivo Android
2. **Premi "AVVIA SERVER"** - Il servizio parte in foreground
3. **Annota l'IP mostrato** (es. 192.168.0.126:5001)
4. **Nell'app controller**: aggiungi il dispositivo con IP e porta 5001
5. **Connetti e invia comandi** - Il dispositivo vibra anche con schermo spento

## 🔧 API Endpoints

### GET /status
Verifica stato del server
```json
{
  "status": "ok",
  "version": "1.0",
  "uptime": 123456,
  "device": "android"
}
```

### POST /command
Invia comando di vibrazione
```json
{
  "letter": "A",
  "settings": {
    "watch": {
      "vibrationMode": true,
      "vibrationPattern": "numeric",
      "vibrationDuration": 300,
      "vibrationPause": 200
    }
  }
}
```

### POST /command (RESET)
Vibrazione singola di reset
```json
{
  "command": "RESET",
  "settings": {
    "watch": {
      "vibrationEnabled": true,
      "vibrationDuration": 700
    }
  }
}
```

## 📳 Pattern Vibrazione

### Numeric
- **A**: 1 vibrazione
- **B**: 2 vibrazioni
- **C**: 3 vibrazioni
- **D**: 4 vibrazioni
- **E**: 5 vibrazioni

### Melodic
- **A**: Lungo-corto-lungo
- **B**: Tre impulsi medi
- **C**: Quattro impulsi brevi
- **D**: Due impulsi lunghi
- **E**: Cinque impulsi rapidi

## 🔋 Gestione Energia

- **WakeLock PARTIAL**: Mantiene CPU attiva, schermo può spegnersi
- **Foreground Service**: Priorità alta, non viene killato facilmente
- **Notifica persistente**: Mostra IP:porta e stato server

## 🔐 Permessi

- `INTERNET` - Server HTTP
- `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` - Rilevamento IP
- `VIBRATE` - Pattern vibrazione
- `WAKE_LOCK` - CPU attiva con schermo spento
- `FOREGROUND_SERVICE` - Servizio prioritario
- `POST_NOTIFICATIONS` - Notifica persistente (Android 13+)

## 📝 Note Tecniche

- **Porta**: 5001 (diversa dal Wear OS watch che usa 5000)
- **minSDK**: 26 (Android 8.0 Oreo)
- **targetSDK**: 34 (Android 14)
- **HTTP Library**: NanoHTTPD 2.3.1
- **Riavvio automatico**: `START_STICKY` se il servizio viene killato

## 🔗 Integrazione

Questa app è compatibile con l'app controller Flutter esistente. Aggiungi semplicemente un nuovo dispositivo con tipo "Android Receiver" e l'IP:porta rilevato.

## 📦 Dipendenze

```gradle
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
com.google.android.material:material:1.11.0
org.nanohttpd:nanohttpd:2.3.1
```
