# Android Receiver - Integrazione con App Controller

## 📝 Note per l'Integrazione

Questo documento descrive come integrare l'Android Receiver nell'app controller Flutter esistente.

## 🎯 Obiettivo

Aggiungere una **terza colonna** nell'interfaccia controller per gestire l'Android Receiver, accanto alle colonne ESP32 e Watch esistenti.

## 🔧 Modifiche Necessarie

### 1. Flutter App - main.dart

Aggiungere supporto per terzo dispositivo Android Receiver:

```dart
// Stato dispositivo Android Receiver
String androidReceiverIp = '192.168.1.100'; // IP configurabile
int androidReceiverPort = 5001;
bool androidReceiverConnected = false;

// Funzione connessione Android Receiver
Future<void> connectAndroidReceiver() async {
  try {
    final uri = Uri.parse('http://$androidReceiverIp:$androidReceiverPort/status');
    final response = await http.get(uri).timeout(Duration(seconds: 5));
    
    if (response.statusCode == 200) {
      final data = json.decode(response.body);
      if (data['status'] == 'ok' && data['device'] == 'android') {
        setState(() {
          androidReceiverConnected = true;
        });
        print('✅ Android Receiver connesso');
      }
    }
  } catch (e) {
    print('❌ Errore connessione Android Receiver: $e');
    setState(() {
      androidReceiverConnected = false;
    });
  }
}

// Funzione invio comando
Future<void> sendToAndroidReceiver(String letter, Map<String, dynamic> settings) async {
  if (!androidReceiverConnected) return;
  
  try {
    final uri = Uri.parse('http://$androidReceiverIp:$androidReceiverPort/command');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: json.encode({
        'letter': letter,
        'settings': settings
      }),
    );
    
    if (response.statusCode == 200) {
      print('✅ Comando inviato ad Android Receiver: $letter');
    }
  } catch (e) {
    print('❌ Errore invio Android Receiver: $e');
  }
}
```

### 2. Layout UI

Aggiungere colonna Android Receiver:

```dart
Row(
  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
  children: [
    // ESP32 Column
    Column(
      children: [
        Text('ESP32'),
        Icon(esp32Connected ? Icons.check_circle : Icons.cancel),
        // ...
      ],
    ),
    // Watch Column
    Column(
      children: [
        Text('Watch (Wear OS)'),
        Icon(watchConnected ? Icons.check_circle : Icons.cancel),
        // ...
      ],
    ),
    // 🆕 Android Receiver Column
    Column(
      children: [
        Text('Android Receiver'),
        Icon(androidReceiverConnected ? Icons.check_circle : Icons.cancel),
        Text('$androidReceiverIp:$androidReceiverPort'),
        ElevatedButton(
          onPressed: connectAndroidReceiver,
          child: Text('Connetti'),
        ),
      ],
    ),
  ],
)
```

### 3. Settings Page

Aggiungere configurazione IP/porta per Android Receiver:

```dart
TextField(
  decoration: InputDecoration(
    labelText: 'Android Receiver IP',
    hintText: '192.168.1.100',
  ),
  controller: androidReceiverIpController,
  onChanged: (value) {
    setState(() {
      androidReceiverIp = value;
    });
  },
),
TextField(
  decoration: InputDecoration(
    labelText: 'Android Receiver Port',
    hintText: '5001',
  ),
  keyboardType: TextInputType.number,
  controller: androidReceiverPortController,
  onChanged: (value) {
    setState(() {
      androidReceiverPort = int.tryParse(value) ?? 5001;
    });
  },
),
```

### 4. Invio Broadcast

Modificare la funzione di invio comandi per includere Android Receiver:

```dart
void sendCommand(String letter) {
  final settings = getSettingsMap();
  
  // Invia a ESP32 (BLE)
  if (esp32Connected) {
    sendToESP32(letter);
  }
  
  // Invia a Watch (Wear OS)
  if (watchConnected) {
    sendToWatch(letter, settings);
  }
  
  // 🆕 Invia a Android Receiver (HTTP)
  if (androidReceiverConnected) {
    sendToAndroidReceiver(letter, settings);
  }
}
```

## 📱 Test Integrazione

1. **Build Android Receiver**:
   ```bash
   cd android_receiver_app
   ./gradlew assembleDebug
   ```

2. **Installa su secondo smartphone**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Avvia server**:
   - Apri app Android Receiver
   - Premi "AVVIA SERVER"
   - Annota IP mostrato (es. 192.168.0.126:5001)

4. **Configura nell'app controller**:
   - Apri impostazioni
   - Inserisci IP e porta del receiver
   - Premi "Connetti" nella colonna Android Receiver

5. **Test invio comandi**:
   - Premi uno dei 5 pulsanti (A-E)
   - Il receiver dovrebbe vibrare secondo il pattern
   - Verifica nei log: `adb logcat | grep ReceiverService`

## 🔍 Debugging

### Log Android Receiver
```bash
adb logcat | grep -E "ReceiverService|AndroidServer|VibrationHandler"
```

### Test endpoint manuale
```bash
# Status check
curl http://192.168.0.126:5001/status

# Invio comando
curl -X POST http://192.168.0.126:5001/command \
  -H "Content-Type: application/json" \
  -d '{"letter":"A","settings":{"watch":{"vibrationMode":true,"vibrationPattern":"numeric","vibrationDuration":300,"vibrationPause":200}}}'
```

## ⚙️ Configurazione Consigliata

- **vibrationMode**: `true` (sempre vibrazione, nessun display)
- **vibrationPattern**: `"numeric"` o `"melodic"` (come watch)
- **vibrationDuration**: `300`ms
- **vibrationPause**: `200`ms

## 🔐 Rete

- Assicurati che tutti i dispositivi siano sulla stessa rete WiFi
- Verifica che il firewall non blocchi la porta 5001
- L'app usa `usesCleartextTraffic="true"` per HTTP

## 📚 Riferimenti

- API Endpoints: vedi `android_receiver_app/README.md`
- Codice sorgente: `android_receiver_app/app/src/main/java/com/example/segnareceiver/`
