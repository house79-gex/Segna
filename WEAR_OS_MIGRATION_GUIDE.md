# Guida alla Migrazione da BLE a Wear OS Data Layer

## 🎯 Obiettivo Raggiunto

L'applicazione è stata **migrata con successo** da BLE GATT Server diretto a **Wear OS Data Layer API** per la comunicazione tra smartphone e smartwatch, risolvendo i conflitti con Samsung Wearable.

## ✅ Vantaggi della Nuova Implementazione

### ✅ Risolve Conflitti Bluetooth
- **Nessun conflitto con Samsung Wearable**: La comunicazione avviene tramite la connessione Wear OS esistente
- **Nessuna necessità di disconnettere il watch**: Il watch rimane connesso a Samsung Wearable
- **Comunicazione affidabile**: Utilizza l'infrastruttura ufficiale Google Wear OS

### ✅ App Watch Sempre Attiva
- **Wakelock attivo**: Il watch rimane attivo anche quando lo schermo è spento
- **Back button disabilitato**: Previene la chiusura accidentale dell'app
- **Chiusura remota**: Solo lo smartphone può chiudere l'app tramite comando specifico

### ✅ Approccio Ibrido
- **Wear OS per Watch**: Comunicazione tramite Message API
- **BLE per ESP32**: Rimane invariata, usa flutter_blue_plus

## 📋 Modifiche Implementate

### Watch App (Wear OS)

#### File: `wear_os_app/app/src/main/java/com/example/watchreceiver/MainActivity.kt`

**Rimozioni:**
- ❌ BluetoothManager, BluetoothAdapter, BluetoothGattServer
- ❌ BluetoothLeAdvertiser, AdvertiseCallback
- ❌ Permessi Bluetooth (BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN)
- ❌ Funzioni setupBLE(), startAdvertising(), sendConfirmation()

**Aggiunte:**
```kotlin
// Wear OS Message Client
private lateinit var messageClient: MessageClient
private var wakeLock: PowerManager.WakeLock? = null

// Implementa MessageClient.OnMessageReceivedListener
override fun onMessageReceived(messageEvent: MessageEvent) {
    if (messageEvent.path == MESSAGE_PATH) {
        val message = String(messageEvent.data, Charsets.UTF_8)
        handleCommand(message)
    }
}

// Wakelock per app sempre attiva
val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "WatchReceiver::WakeLock"
).apply {
    acquire()
}

// Disabilita back button
override fun onBackPressed() {
    // Non fare nulla - app resta aperta
}

// Supporto per comando chiusura remota
if (jsonObject.optBoolean("closeApp", false)) {
    wakeLock?.release()
    finishAndRemoveTask()
    return
}
```

#### File: `wear_os_app/app/src/main/AndroidManifest.xml`

**Modifiche:**
```xml
<!-- Rimossi permessi BLE -->
<!-- BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, etc. -->

<!-- Aggiunti permessi nuovi -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- VIBRATE e Wear OS features rimangono invariati -->
```

### Smartphone App (Flutter)

#### File: `flutter_app/android/app/src/main/kotlin/com/example/segna_ble/MainActivity.kt`

**Implementazione completa:**
```kotlin
class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.segna/wear"
    private val MESSAGE_PATH = "/segna_channel"
    
    private lateinit var messageClient: MessageClient
    private var connectedNodes: List<Node> = emptyList()

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(...).setMethodCallHandler { call, result ->
            when (call.method) {
                "connectToWatch" -> connectToWatch(result)
                "sendMessage" -> sendMessageToWatch(message, result)
                "closeWatchApp" -> closeWatchApp(result)
                "isWatchConnected" -> checkWatchConnection(result)
            }
        }
    }
    
    private fun sendToAllNodes(message: String, result: MethodChannel.Result) {
        for (node in connectedNodes) {
            messageClient.sendMessage(node.id, MESSAGE_PATH, data)
        }
    }
}
```

#### File: `flutter_app/android/app/build.gradle`

**Aggiunta dipendenza:**
```gradle
dependencies {
    implementation 'com.google.android.gms:play-services-wearable:18.1.0'
}
```

#### File: `flutter_app/lib/main.dart`

**Modifiche principali:**
```dart
// Platform channel per Wear OS
static const platform = MethodChannel('com.example.segna/wear');

// Connessione watch tramite Wear OS
Future<void> _connectToWatch() async {
    final result = await platform.invokeMethod('connectToWatch');
    setState(() {
        watchConnected = result == true;
    });
}

// Invio messaggi tramite platform channel
Future<void> _sendToDevices(String payload) async {
    // ESP32 via BLE (invariato)
    if (espCharacteristic != null) {
        await espCharacteristic!.write(bytes);
    }

    // Watch via Wear OS
    if (watchConnected) {
        await platform.invokeMethod('sendMessage', {'message': payload});
    }
}

// Nuovo: chiusura remota app watch
Future<void> _closeWatchApp() async {
    await platform.invokeMethod('closeWatchApp');
}
```

**Modifiche UI:**
```dart
// Nuovo pulsante chiusura watch
if (watchConnected)
    TextButton.icon(
        icon: const Icon(Icons.close, size: 16),
        label: const Text('Chiudi App Watch'),
        onPressed: _closeWatchApp,
    )

// Indicatore stato watch aggiornato
Text('Watch (Wear OS)')  // invece di 'Watch'
```

## 🔧 Come Funziona

### Flusso di Comunicazione

```
┌─────────────────────────────────────────────────┐
│           SMARTPHONE (Flutter)                   │
│  ┌────────────────────────────────────────────┐ │
│  │  UI: Pulsanti A-E + RESET + Chiudi Watch  │ │
│  └────────────────────────────────────────────┘ │
│                     │                            │
│          ┌──────────┴──────────┐                │
│          │                     │                │
│    Platform Channel       BLE (flutter_blue)   │
│   'com.example.segna/wear'     │                │
│          │                     │                │
│          ▼                     ▼                │
│   MessageClient           BluetoothLE          │
│  (Wear OS API)          (ESP32 direct)         │
└──────────┼─────────────────────┼────────────────┘
           │                     │
           │                     │
    ┌──────▼──────┐      ┌──────▼──────┐
    │   WATCH     │      │    ESP32     │
    │  (Wear OS)  │      │ (BLE Server) │
    │             │      │              │
    │ MessageClient│     │  5 LED       │
    │ Listener    │      │  Singoli     │
    └─────────────┘      └──────────────┘
```

### Sequenza Invio Comando

1. **Utente tocca pulsante** (es. lettera "A")
2. **Flutter** crea payload JSON:
   ```json
   {
     "letter": "A",
     "color": "#FFFFFF",
     "settings": {...}
   }
   ```
3. **Smartphone invia a ESP32** via BLE (flutter_blue_plus)
4. **Smartphone invia a Watch** via platform channel:
   ```dart
   platform.invokeMethod('sendMessage', {'message': payload})
   ```
5. **MainActivity.kt** riceve chiamata e usa MessageClient:
   ```kotlin
   messageClient.sendMessage(nodeId, "/segna_channel", data)
   ```
6. **Watch riceve** tramite `onMessageReceived()`:
   ```kotlin
   override fun onMessageReceived(messageEvent: MessageEvent) {
       val message = String(messageEvent.data, Charsets.UTF_8)
       handleCommand(message)
   }
   ```
7. **Watch aggiorna UI** o vibra in base al payload

### Comando Chiusura Watch

1. **Utente tocca** "Chiudi App Watch"
2. **Flutter** chiama:
   ```dart
   platform.invokeMethod('closeWatchApp')
   ```
3. **MainActivity.kt** invia comando speciale:
   ```kotlin
   val closeMessage = """{"closeApp": true}"""
   messageClient.sendMessage(nodeId, MESSAGE_PATH, closeMessage.toByteArray())
   ```
4. **Watch riceve** e si chiude:
   ```kotlin
   if (jsonObject.optBoolean("closeApp", false)) {
       wakeLock?.release()
       finishAndRemoveTask()
   }
   ```

## 📱 Requisiti di Sistema

### Watch (Wear OS)
- **Wear OS 2.0+** (API 25+)
- **Google Play Services** installati
- **Connesso allo smartphone** tramite Wear OS companion app

### Smartphone (Android)
- **Android 5.0+** (API 21+)
- **Google Play Services** installati
- **Wear OS companion app** installata e configurata
- **Watch accoppiato** e connesso

### ESP32 (Invariato)
- **ESP32 DevKit** con BLE
- **5 LED singoli** + resistenze 220Ω
- **Firmware aggiornato** (nessuna modifica necessaria)

## 🚀 Installazione e Setup

### 1. Watch App

```bash
cd wear_os_app
./gradlew assembleRelease

# Installa sul watch
adb connect <IP_WATCH>:5555
adb install app/build/outputs/apk/release/app-release.apk
```

### 2. Smartphone App

```bash
cd flutter_app
flutter pub get
flutter build apk --release

# Installa sullo smartphone
flutter install
```

### 3. Primo Avvio

1. **Apri app watch** sul Galaxy Watch
   - L'app rimane attiva grazie al wakelock
   - Back button è disabilitato
   
2. **Apri app smartphone**
   - Tocca "Connetti Dispositivi"
   - La connessione al watch avviene automaticamente tramite Wear OS
   - La scansione BLE cerca solo l'ESP32

3. **Verifica connessioni**
   - Watch: icona verde con testo "Watch (Wear OS)"
   - ESP32: icona verde con testo "ESP32" (dopo scansione BLE)

4. **Testa funzionalità**
   - Tocca lettere A-E: verifica ricezione su watch e ESP32
   - Tocca Reset: verifica comportamento su entrambi
   - Tocca "Chiudi App Watch": verifica chiusura remota

## 🔍 Troubleshooting

### Watch non si connette

**Problema:** "Watch (Wear OS)" rimane grigio

**Soluzioni:**
1. Verifica che il watch sia connesso allo smartphone:
   - Apri app "Wear OS" sullo smartphone
   - Verifica che il watch sia connesso (icona verde)
   
2. Verifica Google Play Services:
   - Settings > Apps > Google Play Services > deve essere abilitato
   
3. Riavvia entrambi i dispositivi:
   - Riavvia watch
   - Riavvia smartphone
   - Riapri app su entrambi

### Messaggi non arrivano sul watch

**Problema:** Watch connesso ma non riceve lettere

**Soluzioni:**
1. Verifica che l'app watch sia in foreground:
   - L'app deve essere visibile sullo schermo del watch
   
2. Controlla i log Android:
   ```bash
   adb logcat | grep WatchReceiver
   # Dovresti vedere: "Received message: {...}"
   ```

3. Verifica il path del messaggio:
   - Deve essere `/segna_channel` sia su watch che smartphone

### App watch si chiude

**Problema:** App watch si chiude quando si preme back o si cambia app

**Soluzioni:**
1. Verifica che il wakelock sia attivo:
   ```bash
   adb shell dumpsys power | grep "WatchReceiver"
   # Dovresti vedere il wakelock attivo
   ```

2. Verifica che `onBackPressed()` sia sovrascritto:
   - Il metodo deve essere vuoto (non chiama `super.onBackPressed()`)

3. Controlla le impostazioni Wear OS:
   - Settings > Battery > Background usage
   - Imposta app su "Unrestricted"

### ESP32 non si connette

**Problema:** ESP32 rimane grigio dopo scansione

**Soluzioni:**
1. Verifica che l'ESP32 sia acceso e funzionante:
   - Connetti via Serial Monitor (115200 baud)
   - Dovresti vedere "BLE Server pronto!"

2. Verifica i permessi Bluetooth:
   - Android Settings > Apps > Segna > Permissions
   - Abilita: Bluetooth, Location

3. Verifica che il nome dispositivo sia corretto:
   - Nel codice ESP32: `BLEDevice::init("ESP32-Segna")`
   - Nella app Flutter: cerca "ESP32" o "Segna" nel nome

### Comando chiusura non funziona

**Problema:** "Chiudi App Watch" non chiude l'app

**Soluzioni:**
1. Verifica che il watch sia connesso (icona verde)

2. Controlla i log:
   ```bash
   adb logcat | grep "closeApp"
   # Watch dovrebbe ricevere: {"closeApp": true}
   ```

3. Verifica implementazione watch:
   - `handleCommand()` deve controllare `optBoolean("closeApp", false)`
   - Deve chiamare `wakeLock?.release()` e `finishAndRemoveTask()`

## 📊 Confronto: BLE vs Wear OS

| Caratteristica | BLE GATT Server (Vecchio) | Wear OS Message API (Nuovo) |
|----------------|---------------------------|------------------------------|
| **Connessione** | Diretta BLE | Tramite Wear OS |
| **Conflitti Samsung Wearable** | ❌ Sì, causa conflitti | ✅ No, nessun conflitto |
| **Affidabilità** | ⚠️ Media | ✅ Alta |
| **Latenza** | ~50-100ms | ~100-200ms |
| **Range** | ~10m | Illimitato (via phone) |
| **Permessi richiesti** | Bluetooth, Location | Nessun permesso extra |
| **Complessità setup** | Alta | Bassa |
| **Compatibilità** | Tutti device BLE | Solo Wear OS |
| **Supporto ufficiale** | No | ✅ Sì (Google) |

## 🎯 Best Practices

### Performance
- **Batch messaggi**: Se invii molti comandi rapidamente, considera di raggruppare i messaggi
- **Timeout**: Implementa timeout per rilevare disconnessioni
- **Retry logic**: Aggiungi retry automatico in caso di fallimento

### Affidabilità
- **Verifica connessione**: Chiama `isWatchConnected()` prima di inviare messaggi critici
- **Gestione errori**: Cattura eccezioni e mostra messaggi user-friendly
- **Log dettagliati**: Abilita logging per debug in produzione

### User Experience
- **Feedback visivo**: Mostra indicatori di caricamento durante l'invio
- **Conferme ricezione**: Implementa ACK dal watch (opzionale)
- **Offline handling**: Gestisci il caso in cui il watch sia disconnesso

## 📚 Risorse Aggiuntive

### Documentazione Ufficiale
- [Wear OS Message API](https://developer.android.com/training/wearables/data-layer/messages)
- [Wear OS Data Layer](https://developer.android.com/training/wearables/data-layer)
- [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager)

### Tutorial e Guide
- [Building Apps for Wear OS](https://developer.android.com/training/wearables/apps)
- [Flutter Platform Channels](https://docs.flutter.dev/development/platform-integration/platform-channels)

## ✅ Checklist Post-Migrazione

- [x] Rimosso tutto il codice BLE dalla watch app
- [x] Implementato MessageClient sul watch
- [x] Implementato platform channel su Flutter
- [x] Aggiunto wakelock sul watch
- [x] Disabilitato back button sul watch
- [x] Implementato comando chiusura remota
- [x] Aggiunto pulsante "Chiudi App Watch" in UI
- [x] Testato connessione watch via Wear OS
- [x] Verificato assenza conflitti con Samsung Wearable
- [x] Verificato che ESP32 BLE continui a funzionare

## 🎉 Risultato Finale

L'app ora utilizza:
- ✅ **Wear OS Message API** per comunicazione smartphone ↔ watch (nessun conflitto)
- ✅ **BLE diretto** per comunicazione smartphone ↔ ESP32 (funzionalità invariata)
- ✅ **App watch sempre attiva** con wakelock e back button disabilitato
- ✅ **Chiusura remota** dell'app watch tramite comando da smartphone
- ✅ **Compatibilità completa** con Samsung Wearable

**Nessun conflitto Bluetooth. Tutto funziona in parallelo.**
