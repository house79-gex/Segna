package com.example.watchreceiver

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.content.Intent

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val MESSAGE_PATH = "/segna_channel"
    }

    private lateinit var messageClient: MessageClient
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiReceiver: WiFiReceiver? = null
    private var watchServer: WatchServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Message Client
        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)

        // Initialize Vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        // Acquire wake lock to keep app active
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WatchReceiver::WakeLock"
        ).apply {
            acquire()
        }

        // Start Watch HTTP Server
        watchServer = WatchServer { message ->
            handleCommand(message)
        }
        watchServer?.start(5000)
        Log.d(TAG, "Server HTTP watch avviato")

        // Load saved ESP32 IP from settings
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val esp32Ip = prefs.getString("esp32_ip", "192.168.0.100") ?: "192.168.0.100"

        // Initialize WiFi Receiver for polling
        wifiReceiver = WiFiReceiver(esp32Ip) { letter, color ->
            runOnUiThread {
                handleLetterReceived(letter, color)
            }
        }
        wifiReceiver?.startPolling()

        // Compose UI
        setContent {
            WatchReceiverScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        wifiReceiver?.stopPolling()
        watchServer?.stop()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onBackPressed() {
        // Disable back button to prevent accidental app closure
        // App can only be closed via remote command or manual force stop
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == MESSAGE_PATH) {
            val message = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "📨 Messaggio ricevuto via Wear OS: $message")
            handleCommand(message)
        }
    }

    private fun handleCommand(message: String) {
        try {
            val jsonObject = JSONObject(message)

            // Check for close app command
            if (jsonObject.optBoolean("closeApp", false)) {
                Log.d(TAG, "Comando chiusura app ricevuto")
                wakeLock?.release()
                finishAndRemoveTask()
                return
            }

            if (jsonObject.has("command") && jsonObject.getString("command") == "RESET") {
                // Gestione comando RESET
                android.util.Log.d(TAG, "Processing RESET command")
                
                val settings = if (jsonObject.has("settings")) {
                    jsonObject.getJSONObject("settings")
                } else null

                if (settings != null && settings.has("watch")) {
                    val watchSettings = settings.getJSONObject("watch")
                    val vibrationEnabled = watchSettings.optBoolean("vibrationEnabled", false)
                    val vibrationDuration = watchSettings.optInt("vibrationDuration", 800)

                    if (vibrationEnabled && vibrator?.hasVibrator() == true) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(
                            vibrationDuration.toLong(),
                            VibrationEffect.DEFAULT_AMPLITUDE
                        ))
                    }
                }

                runOnUiThread {
                    letterState.value = ""
                    colorState.value = android.graphics.Color.BLACK
                    isVibrationMode.value = false
                    
                    Toast.makeText(this, "RESET eseguito", Toast.LENGTH_SHORT).show()
                }

            } else if (jsonObject.has("letter")) {
                val letter = jsonObject.getString("letter")
                val colorHex = jsonObject.getString("color")
                val color = android.graphics.Color.parseColor(colorHex)

                // Leggi impostazioni
                val settings = if (jsonObject.has("settings")) {
                    jsonObject.getJSONObject("settings")
                } else null

                var vibrationMode = false
                var vibrationPattern = "numeric"
                var vibrationDuration = 300
                var vibrationPause = 200

                if (settings != null && settings.has("watch")) {
                    val watchSettings = settings.getJSONObject("watch")
                    vibrationMode = watchSettings.optBoolean("vibrationMode", false)
                    vibrationPattern = watchSettings.optString("vibrationPattern", "numeric")
                    vibrationDuration = watchSettings.optInt("vibrationDuration", 300)
                    vibrationPause = watchSettings.optInt("vibrationPause", 200)
                }

                if (vibrationMode && vibrator?.hasVibrator() == true) {
                    android.util.Log.d(TAG, "Vibration mode activated")
                    
                    // Modalità vibrazione: schermo nero
                    runOnUiThread {
                        isVibrationMode.value = true
                        colorState.value = android.graphics.Color.BLACK
                        letterState.value = ""
                    }

                    android.util.Log.d(TAG, "Pattern vibrazione: $vibrationPattern")

                    // Costruisci pattern vibrazioni
                    val pattern: LongArray = when (vibrationPattern) {
                        "morse" -> {
                            when (letter) {
                                "A" -> longArrayOf(0, 100)
                                "B" -> longArrayOf(0, 100, 100, 100)
                                "C" -> longArrayOf(0, 500)
                                "D" -> longArrayOf(0, 500, 100, 100)
                                "E" -> longArrayOf(0, 100, 100, 500)
                                else -> longArrayOf(0, vibrationDuration.toLong())
                            }
                        }
                        
                        "intensity" -> {
                            when (letter) {
                                "A" -> longArrayOf(0, 200)
                                "B" -> longArrayOf(0, 300)
                                "C" -> longArrayOf(0, 400)
                                "D" -> longArrayOf(0, 600)
                                "E" -> longArrayOf(0, 400, 200, 400)
                                else -> longArrayOf(0, vibrationDuration.toLong())
                            }
                        }
                        
                        "melodic" -> {
                            when (letter) {
                                "A" -> longArrayOf(0, 50, 50, 50)
                                "B" -> longArrayOf(0, 100, 100, 200, 100, 100)
                                "C" -> longArrayOf(0, 120, 80, 120)
                                "D" -> longArrayOf(0, 200, 100, 200, 100, 200)
                                "E" -> longArrayOf(0, 100, 300, 100)
                                else -> longArrayOf(0, vibrationDuration.toLong())
                            }
                        }
                        
                        else -> {
                            // "numeric" (default)
                            val vibrationCount = when (letter) {
                                "A" -> 1
                                "B" -> 2
                                "C" -> 3
                                "D" -> 4
                                "E" -> 5
                                else -> 1
                            }
                            
                            val patternList = mutableListOf<Long>()
                            patternList.add(0)
                            
                            for (i in 0 until vibrationCount) {
                                patternList.add(vibrationDuration.toLong())
                                if (i < vibrationCount - 1) {
                                    patternList.add(vibrationPause.toLong())
                                }
                            }
                            
                            patternList.toLongArray()
                        }
                    }

                    // Esegui vibrazione
                    try {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Errore vibrazione: ${e.message}")
                    }
                    
                } else {
                    // Modalità display normale
                    android.util.Log.d(TAG, "Display mode activated")
                    
                    runOnUiThread {
                        isVibrationMode.value = false
                        letterState.value = letter
                        colorState.value = color
                        
                        Toast.makeText(
                            this, 
                            "Mostro: $letter", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore parsing messaggio: ${e.message}")
        }
    }

    private fun handleLetterReceived(letter: String, colorHex: String) {
        try {
            val color = Color.parseColor(colorHex)
            letterState.value = letter
            colorState.value = color
            isVibrationMode.value = false
            
            Toast.makeText(
                this,
                "Ricevuto via WiFi: $letter",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Errore conversione colore: ${e.message}")
        }
    }

    // Compose State
    private val letterState = mutableStateOf("")
    private val colorState = mutableStateOf(Color.BLACK)
    private val isVibrationMode = mutableStateOf(false)

    @Composable
    fun WatchReceiverScreen() {
        var offsetX by remember { mutableStateOf(0f) }
        val swipeThreshold = 300f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = ComposeColor(colorState.value)
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > swipeThreshold) {
                                // Swipe right → Settings
                                val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                                startActivity(intent)
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isVibrationMode.value) {
                // Modalità vibrazione: schermo nero senza testo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor.Black)
                )
            } else if (letterState.value.isNotEmpty()) {
                // Modalità display: mostra lettera
                Text(
                    text = letterState.value,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (letterState.value) {
                        "A", "B" -> ComposeColor.Black
                        else -> ComposeColor.White
                    }
                )
            } else {
                // Stato iniziale: schermo nero
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Segna",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "In attesa...",
                        fontSize = 14.sp,
                        color = ComposeColor.Gray
                    )
                }
            }

            // Indicatore swipe per impostazioni
            if (offsetX > 50f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(48.dp)
                        .background(
                            color = ComposeColor.White.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙️",
                        fontSize = 24.sp
                    )
                }
            }
        }
    }
}
