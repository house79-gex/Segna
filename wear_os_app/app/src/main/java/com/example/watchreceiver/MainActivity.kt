package com.example.watchreceiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {
    private var vibrator: Vibrator? = null
    private var notificationManager: NotificationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var messageClient: MessageClient

    private val letterState = mutableStateOf("")
    private val colorState = mutableStateOf(android.graphics.Color.BLACK)
    private val isVibrationMode = mutableStateOf(false)
    
    // Settings
    private val displayModeState = mutableStateOf("BOTH")
    private val letterSizeState = mutableStateOf(120)
    private val colorSizeState = mutableStateOf("FULLSCREEN")

    companion object {
        private const val MESSAGE_PATH = "/segna_channel"
        private const val TAG = "WatchReceiver"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Acquire wakelock to keep app active
        // Using timeout to prevent battery drain if app crashes
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WatchReceiver::WakeLock"
        ).apply {
            acquire(10*60*1000L /*10 minutes*/)
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Initialize Wear OS Message Client
        messageClient = Wearable.getMessageClient(this)
        messageClient.addListener(this)
        
        android.util.Log.d(TAG, "Wear OS MessageClient initialized")
        
        // ⭐ Toast all'avvio
        Toast.makeText(this, "WatchReceiver avviato", Toast.LENGTH_SHORT).show()
        
        // Load settings
        loadSettings()

        setContent {
            WatchDisplay(
                letter = letterState.value,
                androidColor = colorState.value,
                isVibrationMode = isVibrationMode.value,
                displayMode = displayModeState.value,
                letterSize = letterSizeState.value,
                colorSize = colorSizeState.value,
                onSettingsClick = { openSettings() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Cancel all notifications when app is in foreground
        notificationManager?.cancelAll()
        // Reload settings in case they changed
        loadSettings()
        // Re-add listener
        messageClient.addListener(this)
        
        // Re-acquire wakelock if it expired
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10*60*1000L /*10 minutes*/)
            android.util.Log.d(TAG, "Wakelock re-acquired")
        }
    }

    override fun onPause() {
        super.onPause()
        // Remove listener to avoid leaks
        messageClient.removeListener(this)
    }

    override fun onDestroy() {
        wakeLock?.release()
        messageClient.removeListener(this)
        super.onDestroy()
    }

    // Override back button to prevent accidental closure
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Do nothing - app stays open
        // Only remote command from smartphone can close it
        android.util.Log.d(TAG, "Back button pressed - ignoring to keep app active")
    }

    // Wear OS Message Listener
    override fun onMessageReceived(messageEvent: MessageEvent) {
        android.util.Log.d(TAG, "onMessageReceived called - path: ${messageEvent.path}")
        
        // ⭐ Toast per confermare ricezione
        runOnUiThread {
            Toast.makeText(
                this, 
                "Msg ricevuto: ${messageEvent.path}", 
                Toast.LENGTH_LONG
            ).show()
        }
        
        if (messageEvent.path == MESSAGE_PATH) {
            val message = String(messageEvent.data, Charsets.UTF_8)
            android.util.Log.d(TAG, "Received message: $message")
            
            // ⭐ Toast con contenuto messaggio
            runOnUiThread {
                Toast.makeText(
                    this, 
                    "Dati: ${message.take(50)}", 
                    Toast.LENGTH_LONG
                ).show()
            }
            
            handleCommand(message)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        displayModeState.value = prefs.getString("display_mode", "BOTH") ?: "BOTH"
        letterSizeState.value = prefs.getInt("letter_size", 120)
        colorSizeState.value = prefs.getString("color_size", "FULLSCREEN") ?: "FULLSCREEN"
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun handleCommand(json: String) {
        try {
            val jsonObject = JSONObject(json)
            
            android.util.Log.d(TAG, "Parsing JSON: $json")
            
            // ⭐ Toast per parsing
            runOnUiThread {
                val letter = jsonObject.optString("letter", "?")
                Toast.makeText(
                    this, 
                    "Parsing lettera: $letter", 
                    Toast.LENGTH_LONG
                ).show()
            }

            // Handle close app command
            if (jsonObject.optBoolean("closeApp", false)) {
                android.util.Log.d(TAG, "Received closeApp command")
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
                    
                    // ⭐ Toast RESET
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
                var vibrationDuration = 300
                var vibrationPause = 200

                if (settings != null && settings.has("watch")) {
                    val watchSettings = settings.getJSONObject("watch")
                    vibrationMode = watchSettings.optBoolean("vibrationMode", false)
                    vibrationDuration = watchSettings.optInt("vibrationDuration", 300)
                    vibrationPause = watchSettings.optInt("vibrationPause", 200)
                }

                if (vibrationMode && vibrator?.hasVibrator() == true) {
                    android.util.Log.d(TAG, "Vibration mode activated")
                    
                    // Modalità vibrazione: schermo nero + vibrazioni
                    runOnUiThread {
                        isVibrationMode.value = true
                        colorState.value = android.graphics.Color.BLACK
                        letterState.value = ""
                    }

                    // Determina numero di vibrazioni in base alla lettera
                    val vibrationCount = when (letter) {
                        "A" -> 1
                        "B" -> 2
                        "C" -> 3
                        "D" -> 4
                        "E" -> 5
                        else -> 1
                    }

                    // Esegui vibrazioni in coroutine
                    CoroutineScope(Dispatchers.Main).launch {
                        for (i in 0 until vibrationCount) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(
                                vibrationDuration.toLong(),
                                VibrationEffect.DEFAULT_AMPLITUDE
                            ))
                            if (i < vibrationCount - 1) {
                                delay(vibrationPause.toLong())
                            }
                        }
                    }
                } else {
                    android.util.Log.d(TAG, "Display mode activated")
                    
                    // Modalità display: lettera e colore
                    runOnUiThread {
                        isVibrationMode.value = false
                        letterState.value = letter
                        colorState.value = color
                        
                        // ⭐ Toast prima di mostrare lettera
                        Toast.makeText(
                            this, 
                            "Mostro: $letter", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing message", e)
            
            // ⭐ Toast errore
            runOnUiThread {
                Toast.makeText(
                    this, 
                    "ERRORE: ${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

@Composable
fun WatchDisplay(
    letter: String,
    androidColor: Int,
    isVibrationMode: Boolean,
    displayMode: String,
    letterSize: Int,
    colorSize: String,
    onSettingsClick: () -> Unit
) {
    // Determine what to show based on display mode
    val showLetter = !isVibrationMode && letter.isNotEmpty() && 
                     (displayMode == "BOTH" || displayMode == "LETTER_ONLY")
    val showColor = !isVibrationMode && (displayMode == "BOTH" || displayMode == "COLOR_ONLY")

    val backgroundColor = if (isVibrationMode) {
        ComposeColor.Black
    } else if (displayMode == "LETTER_ONLY") {
        ComposeColor.Black
    } else if (showColor) {
        ComposeColor(
            red = android.graphics.Color.red(androidColor) / 255f,
            green = android.graphics.Color.green(androidColor) / 255f,
            blue = android.graphics.Color.blue(androidColor) / 255f,
            alpha = 1f
        )
    } else {
        ComposeColor.Black
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (colorSize == "FULLSCREEN" || displayMode == "LETTER_ONLY" || isVibrationMode) {
                    backgroundColor
                } else {
                    ComposeColor.Black
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Show colored circle if not fullscreen
        if (showColor && colorSize != "FULLSCREEN") {
            val radius = when (colorSize) {
                "CIRCLE_LARGE" -> 150.dp
                "CIRCLE_MEDIUM" -> 100.dp
                "CIRCLE_SMALL" -> 50.dp
                else -> 100.dp
            }
            
            Box(
                modifier = Modifier
                    .size(radius * 2)
                    .clip(CircleShape)
                    .background(backgroundColor)
            )
        }

        // Show letter if needed
        if (showLetter) {
            Text(
                text = letter,
                fontSize = letterSize.sp,
                fontWeight = FontWeight.Bold,
                color = if (androidColor == android.graphics.Color.WHITE ||
                    androidColor == android.graphics.Color.YELLOW ||
                    androidColor == android.graphics.Color.GREEN
                ) {
                    ComposeColor.Black
                } else {
                    ComposeColor.White
                }
            )
        }

        // Settings button in top-right corner
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Impostazioni",
                tint = ComposeColor.White
            )
        }
    }
}
