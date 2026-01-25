package com.example.watchreceiver

import android.content.Context
import android.content.Intent
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
import org.json.JSONObject

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

    private val letterState = mutableStateOf("")
    private val colorState = mutableStateOf(Color.BLACK)
    private val isVibrationMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WatchReceiver::WakeLock"
        ).apply {
            acquire()
        }

        watchServer = WatchServer { message ->
            handleCommand(message)
        }
        watchServer?.start(5000)
        Log.d(TAG, "Server HTTP watch avviato")

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val esp32Ip = prefs.getString("esp32_ip", "192.168.0.100") ?: "192.168.0.100"

        wifiReceiver = WiFiReceiver(esp32Ip) { letter, color ->
            runOnUiThread {
                handleLetterReceived(letter, color)
            }
        }
        wifiReceiver?.startPolling()

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
        // Disabled
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

            if (jsonObject.optBoolean("closeApp", false)) {
                Log.d(TAG, "Comando chiusura app ricevuto")
                wakeLock?.release()
                finishAndRemoveTask()
                return
            }

            if (jsonObject.has("command") && jsonObject.getString("command") == "RESET") {
                Log.d(TAG, "Processing RESET command")
                
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
                    colorState.value = Color.BLACK
                    isVibrationMode.value = false
                    Toast.makeText(this, "RESET eseguito", Toast.LENGTH_SHORT).show()
                }

            } else if (jsonObject.has("letter")) {
                val letter = jsonObject.getString("letter")
                val colorHex = jsonObject.getString("color")
                val color = Color.parseColor(colorHex)

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
                    Log.d(TAG, "Vibration mode activated: $vibrationPattern")
                    
                    runOnUiThread {
                        isVibrationMode.value = true
                        colorState.value = Color.BLACK
                        letterState.value = ""
                    }

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

                    try {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore vibrazione: ${e.message}")
                    }
                    
                } else {
                    Log.d(TAG, "Display mode activated")
                    
                    runOnUiThread {
                        isVibrationMode.value = false
                        letterState.value = letter
                        colorState.value = color
                        Toast.makeText(this, "Mostro: $letter", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore parsing messaggio: ${e.message}")
        }
    }

    private fun handleLetterReceived(letter: String, colorHex: String) {
        try {
            val color = Color.parseColor(colorHex)
            letterState.value = letter
            colorState.value = color
            isVibrationMode.value = false
            Toast.makeText(this, "Ricevuto via WiFi: $letter", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Errore conversione colore: ${e.message}")
        }
    }

    @Composable
    fun WatchReceiverScreen() {
        var offsetX by remember { mutableStateOf(0f) }
        val swipeThreshold = 300f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = ComposeColor(colorState.value))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > swipeThreshold) {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor.Black)
                )
            } else if (letterState.value.isNotEmpty()) {
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
                    Text(text = "⚙️", fontSize = 24.sp)
                }
            }
        }
    }
}
