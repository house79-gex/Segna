package com.example.watchreceiver

import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private var watchServer: WatchServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    
    // Stato UI
    private val _currentLetter = mutableStateOf("?")
    private val _currentColor = mutableStateOf(Color.Gray)
    private val _isDisplayMode = mutableStateOf(true)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 Avvio Watch Receiver")
        
        // Schermo sempre acceso (solo window flags, no BRIGHT_WAKE_LOCK)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        Log.d(TAG, "🔒 Window flags impostati")
        
        // Inizializza vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Avvia server HTTP
        watchServer = WatchServer { message ->
            handleReceivedMessage(message)
        }
        
        try {
            watchServer?.start()
            Log.d(TAG, "✅ Server HTTP avviato su porta 5000")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore avvio server: ${e.message}", e)
        }
        
        // WakeLock PARTIAL (leggero, solo per server)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WatchReceiver::ServerWakeLock"
        )
        wakeLock?.acquire()
        Log.d(TAG, "🔓 WakeLock PARTIAL acquisito")
        
        setContent {
            WatchReceiverScreen(
                letter = _currentLetter.value,
                color = _currentColor.value,
                isDisplayMode = _isDisplayMode.value
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "▶️ onResume")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        watchServer?.stop()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        Log.d(TAG, "🛑 Server fermato")
    }
    
    private fun handleReceivedMessage(message: String) {
        try {
            Log.d(TAG, "📥 Messaggio: $message")
            
            val json = JSONObject(message)
            
            // Comando RESET
            if (json.has("command") && json.getString("command") == "RESET") {
                Log.d(TAG, "🔄 RESET")
                handleReset(json)
                return
            }
            
            // Comando lettera
            if (json.has("letter") && json.has("color")) {
                val letter = json.getString("letter")
                val colorHex = json.getString("color")
                
                Log.d(TAG, "🎨 $letter - $colorHex")
                
                // Leggi settings
                val settings = if (json.has("settings")) {
                    json.getJSONObject("settings").getJSONObject("watch")
                } else null
                
                val vibrationMode = settings?.optBoolean("vibrationMode", false) ?: false
                
                if (vibrationMode) {
                    // Modalità vibrazione
                    _isDisplayMode.value = false
                    handleVibration(letter, settings)
                    Log.d(TAG, "📳 Vibration mode activated")
                } else {
                    // Modalità display
                    _isDisplayMode.value = true
                    _currentLetter.value = letter
                    _currentColor.value = parseColor(colorHex)
                    Log.d(TAG, "📺 Display mode activated")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore: ${e.message}", e)
        }
    }
    
    private fun handleReset(json: JSONObject) {
        _currentLetter.value = "?"
        _currentColor.value = Color.Gray
        _isDisplayMode.value = true
        
        // Vibrazione reset (lunga)
        try {
            val settings = if (json.has("settings")) {
                json.getJSONObject("settings").getJSONObject("watch")
            } else null
            
            val duration = settings?.optInt("vibrationDuration", 700) ?: 700
            
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    duration.toLong(),
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            Log.d(TAG, "📳 Reset vibration: ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore vibrazione reset: ${e.message}")
        }
    }
    
    private fun handleVibration(letter: String, settings: JSONObject?) {
        try {
            val pattern = settings?.optString("vibrationPattern", "numeric") ?: "numeric"
            val duration = settings?.optInt("vibrationDuration", 300) ?: 300
            val pause = settings?.optInt("vibrationPause", 200) ?: 200
            
            val count = when (letter) {
                "A" -> 1
                "B" -> 2
                "C" -> 3
                "D" -> 4
                "E" -> 5
                else -> 1
            }
            
            val timings = if (pattern == "melodic") {
                // Pattern melodico
                when (letter) {
                    "A" -> longArrayOf(0, 200, 100, 200)
                    "B" -> longArrayOf(0, 150, 100, 150, 100, 150)
                    "C" -> longArrayOf(0, 100, 50, 100, 50, 100, 50, 100)
                    "D" -> longArrayOf(0, 300, 150, 300)
                    "E" -> longArrayOf(0, 100, 100, 100, 100, 100)
                    else -> longArrayOf(0, 300)
                }
            } else {
                // Pattern numerico
                val pattern = mutableListOf<Long>()
                pattern.add(0) // Delay iniziale
                repeat(count) {
                    pattern.add(duration.toLong())
                    if (it < count - 1) {
                        pattern.add(pause.toLong())
                    }
                }
                pattern.toLongArray()
            }
            
            vibrator?.vibrate(
                VibrationEffect.createWaveform(timings, -1)
            )
            
            Log.d(TAG, "📳 Vibrazione $pattern: $letter ($count volte)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore vibrazione: ${e.message}", e)
        }
    }
    
    private fun parseColor(hex: String): Color {
        return try {
            val cleanHex = hex.removePrefix("#")
            val colorInt = android.graphics.Color.parseColor("#$cleanHex")
            Color(colorInt)
        } catch (e: Exception) {
            Color.Gray
        }
    }
}

@Composable
fun WatchReceiverScreen(
    letter: String = "?",
    color: Color = Color.Gray,
    isDisplayMode: Boolean = true
) {
    Scaffold(
        timeText = { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isDisplayMode) {
                // Modalità display: mostra lettera colorata
                Text(
                    text = letter,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Center
                )
            } else {
                // Modalità vibrazione: schermo nero
                Text(
                    text = "📳",
                    fontSize = 60.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
