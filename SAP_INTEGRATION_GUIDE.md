# Samsung Accessory Protocol (SAP) Integration Guide

## ⚠️ IMPORTANTE: Implementazione SAP vs BLE

Questo progetto è stato progettato per utilizzare **Samsung Accessory Protocol (SAP)** per evitare conflitti con Samsung Wearable. Tuttavia, l'implementazione completa di SAP richiede:

1. **Samsung Accessory Protocol SDK** - Disponibile solo tramite Samsung Developer Program
2. **Partnership Samsung** - Richiede registrazione e approvazione
3. **Certificazione app** - Le app SAP devono essere certificate da Samsung

### Stato Attuale

Il codice attuale utilizza **BLE GATT Server diretto** che funziona ma può causare conflitti con Samsung Wearable quando entrambi sono attivi contemporaneamente.

### Soluzioni Disponibili

#### Opzione 1: SAP (Raccomandato per produzione)
Per implementare SAP completamente:

1. **Registrati al Samsung Developer Program**: https://developer.samsung.com/
2. **Scarica l'Accessory SDK**: https://developer.samsung.com/accessory/download.html
3. **Richiedi l'approvazione** per l'uso di SAP
4. **Implementa SAP** seguendo questa guida

#### Opzione 2: BLE con gestione conflitti (Attuale)
L'implementazione attuale usa BLE e può funzionare se:
- Samsung Wearable è temporaneamente disconnesso durante l'uso dell'app
- Oppure l'app Segna Watch viene installata come app standalone

#### Opzione 3: Wear OS Message API (Alternativa consigliata)
Invece di SAP, si può usare l'API ufficiale Wear OS per la comunicazione tra smartphone e watch:
- **MessageClient** di Wear OS per messaggi istantanei
- **DataClient** per sincronizzazione dati
- **ChannelClient** per streaming dati

Questa è l'approccio più standard e supportato per le app Wear OS.

---

## 📋 Implementazione SAP (Quando disponibile l'SDK)

### Watch App (Wear OS) - SAP Consumer

#### 1. Aggiungi dipendenze in `wear_os_app/app/build.gradle`

```gradle
dependencies {
    // Samsung Accessory Protocol
    implementation 'com.samsung.android:companion-library:2.0.0'
    
    // Existing dependencies...
    implementation 'androidx.core:core-ktx:1.12.0'
    // ...
}
```

#### 2. Crea SAP Service Provider per il Watch

Crea `SAPServiceProvider.kt`:

```kotlin
package com.example.watchreceiver

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.samsung.android.sdk.accessory.*
import org.json.JSONObject

class SAPServiceProvider : SAAgent(TAG) {
    
    private var mConnectionHandler: SAPConnectionHandler? = null
    private val mBinder: IBinder = LocalBinder()
    
    companion object {
        private const val TAG = "SAPServiceProvider"
        private const val CHANNEL_ID = 123 // Unique channel ID
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): SAPServiceProvider = this@SAPServiceProvider
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }
    
    override fun onCreate() {
        super.onCreate()
        
        val accessory = SA()
        try {
            accessory.initialize(this)
        } catch (e: SsdkUnsupportedException) {
            Log.e(TAG, "Samsung Accessory SDK non supportato", e)
            stopSelf()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Errore inizializzazione SAP", e)
            stopSelf()
            return
        }
    }
    
    override fun onFindPeerAgentsResponse(peerAgents: Array<out SAPeerAgent>?, result: Int) {
        Log.d(TAG, "onFindPeerAgentsResponse: result=$result")
    }
    
    override fun onServiceConnectionRequested(peerAgent: SAPeerAgent?) {
        if (peerAgent != null) {
            acceptServiceConnectionRequest(peerAgent)
        }
    }
    
    override fun onServiceConnectionResponse(
        peerAgent: SAPeerAgent?,
        connection: SASocket?,
        result: Int
    ) {
        if (result == SAAgent.CONNECTION_SUCCESS && connection != null) {
            mConnectionHandler = SAPConnectionHandler(connection)
            Log.d(TAG, "Connessione SAP stabilita con successo")
        } else {
            Log.e(TAG, "Errore connessione SAP: $result")
        }
    }
    
    inner class SAPConnectionHandler(socket: SASocket) : SASocket(socket) {
        
        override fun onReceive(channelId: Int, data: ByteArray?) {
            if (data != null) {
                val json = String(data)
                Log.d(TAG, "Ricevuto messaggio: $json")
                handleMessage(json)
            }
        }
        
        override fun onError(channelId: Int, errorString: String?, errorCode: Int) {
            Log.e(TAG, "Errore SAP: $errorString (code: $errorCode)")
        }
        
        private fun handleMessage(json: String) {
            // Processa il messaggio ricevuto
            // Questa logica è simile a handleCommand() in MainActivity
            try {
                val jsonObject = JSONObject(json)
                // Notifica MainActivity per aggiornare UI
                val intent = Intent("com.example.watchreceiver.UPDATE_DISPLAY")
                intent.putExtra("json", json)
                sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Errore parsing JSON", e)
            }
        }
        
        fun sendMessage(message: String) {
            try {
                send(CHANNEL_ID, message.toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "Errore invio messaggio", e)
            }
        }
    }
}
```

#### 3. Modifica MainActivity per usare SAP

Aggiungi in `MainActivity.kt`:

```kotlin
class MainActivity : ComponentActivity() {
    private var sapService: SAPServiceProvider? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SAPServiceProvider.LocalBinder
            sapService = binder.getService()
            isBound = true
            Log.d("MainActivity", "SAP Service connesso")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            sapService = null
            isBound = false
        }
    }
    
    // Ricevi messaggi da SAP
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val json = intent?.getStringExtra("json")
            if (json != null) {
                handleCommand(json)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bind al SAP Service
        val intent = Intent(this, SAPServiceProvider::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        // Registra receiver per messaggi SAP
        registerReceiver(messageReceiver, IntentFilter("com.example.watchreceiver.UPDATE_DISPLAY"))
        
        // ... resto del codice ...
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        }
        unregisterReceiver(messageReceiver)
    }
}
```

#### 4. Aggiungi Service in AndroidManifest.xml

```xml
<service
    android:name=".SAPServiceProvider"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="com.samsung.android.sdk.accessory.ServiceProvider" />
    </intent-filter>
</service>
```

#### 5. Crea file XML per SAP Service

Crea `res/xml/accessoryservices.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <application name="Segna Watch">
        <serviceProfile
            name="SegnaService"
            version="1.0"
            id="/segna/watch"
            role="consumer">
            <transport type="TRANSPORT_BT" />
            <transport type="TRANSPORT_WIFI" />
        </serviceProfile>
    </application>
</resources>
```

### Smartphone App (Flutter) - SAP Provider

#### 1. Crea Platform Channel in Flutter

In `lib/main.dart`:

```dart
import 'package:flutter/services.dart';

class SAPManager {
  static const platform = MethodChannel('com.example.segna/sap');
  
  Future<void> connectToWatch() async {
    try {
      await platform.invokeMethod('connectToWatch');
    } catch (e) {
      print('Errore connessione SAP: $e');
    }
  }
  
  Future<void> sendMessage(Map<String, dynamic> message) async {
    try {
      await platform.invokeMethod('sendMessage', {'json': jsonEncode(message)});
    } catch (e) {
      print('Errore invio messaggio SAP: $e');
    }
  }
}
```

#### 2. Implementa SAP Provider in Android Native

Crea `android/app/src/main/kotlin/com/example/segna_ble/SAPProviderService.kt`:

```kotlin
package com.example.segna_ble

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.samsung.android.sdk.accessory.*

class SAPProviderService : SAAgent(TAG) {
    
    private var connectionHandler: SAPConnectionHandler? = null
    private val binder: IBinder = LocalBinder()
    
    companion object {
        private const val TAG = "SAPProviderService"
        private const val CHANNEL_ID = 123
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): SAPProviderService = this@SAPProviderService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        
        val accessory = SA()
        try {
            accessory.initialize(this)
        } catch (e: Exception) {
            Log.e(TAG, "Errore inizializzazione SAP", e)
            stopSelf()
        }
    }
    
    // Implementazione simile al watch, ma come Provider
    // ...
    
    fun sendMessageToWatch(json: String) {
        connectionHandler?.sendMessage(json)
    }
}
```

#### 3. Integra con MainActivity.kt

In `android/app/src/main/kotlin/com/example/segna_ble/MainActivity.kt`:

```kotlin
class MainActivity : FlutterActivity() {
    private var sapService: SAPProviderService? = null
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.example.segna/sap")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "connectToWatch" -> {
                        // Inizia ricerca peer agents
                        result.success(true)
                    }
                    "sendMessage" -> {
                        val json = call.argument<String>("json")
                        sapService?.sendMessageToWatch(json ?: "")
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
```

---

## 🔄 Alternativa: Wear OS Message API (Consigliato)

Se non hai accesso a SAP SDK, l'approccio migliore è usare Wear OS Message API:

### Watch App

```gradle
dependencies {
    implementation 'com.google.android.gms:play-services-wearable:18.1.0'
}
```

```kotlin
class MessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/segna_channel") {
            val json = String(messageEvent.data)
            // Processa messaggio
        }
    }
}
```

### Smartphone App

```kotlin
val nodeClient = Wearable.getNodeClient(context)
val messageClient = Wearable.getMessageClient(context)

// Invia messaggio
nodeClient.connectedNodes.addOnSuccessListener { nodes ->
    nodes.forEach { node ->
        messageClient.sendMessage(node.id, "/segna_channel", json.toByteArray())
    }
}
```

---

## 📝 Note Finali

- **SAP richiede certificazione Samsung** - Non disponibile per sviluppatori individuali senza partnership
- **Wear OS Message API è standard** - Funziona su tutti i device Wear OS
- **BLE diretto funziona** ma può causare conflitti con Samsung Wearable
- **Testare sempre su device reali** - Gli emulatori non supportano SAP

Per assistenza su SAP: https://developer.samsung.com/galaxy-watch
