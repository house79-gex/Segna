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
import androidx.compose.ui.unit.dp
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
    private val _fontSize = mutableStateOf(80)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 Avvio Watch Receiver")
        
        // Schermo sempre acceso
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
            Log.d(TAG, "⌚ Server HTTP watch avviato su porta 5000")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore avvio server: ${e.message}", e)
        }
        
        // WakeLock PARTIAL
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WatchReceiver::ServerWakeLock"
        )
        wakeLock?.acquire()
        Log.d(TAG, "🔓 WakeLock PARTIAL acquisito")
        
        setContent {
            WatchReceiverApp(
                letter = _currentLetter.value,
                color = _currentColor.value,
                isDisplayMode = _isDisplayMode.value,
                fontSize = _fontSize.value,
                onFontSizeChange = { newSize ->
                    _fontSize.value = newSize
                }
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
            Log.d(TAG, "📥 Messaggio ricevuto: $message")
            
            val json = JSONObject(message)
            
            // Comando RESET
            if (json.has("command") && json.getString("command") == "RESET") {
                Log.d(TAG, "Processing RESET command")
                handleReset(json)
                return
            }
            
            // Comando lettera
            if (json.has("letter") && json.has("color")) {
                val letter = json.getString("letter")
                val colorHex = json.getString("color")
                
                // Leggi settings
                val settings = if (json.has("settings")) {
                    json.getJSONObject("settings").getJSONObject("watch")
                } else null
                
                val vibrationMode = settings?.optBoolean("vibrationMode", false) ?: false
                
                if (vibrationMode) {
                    // Modalità vibrazione SOLO
                    Log.d(TAG, "Vibration mode activated: ${settings?.optString("vibrationPattern", "numeric")}")
                    _isDisplayMode.value = false
                    handleVibration(letter, settings)
                } else {
                    // Modalità display SOLO
                    Log.d(TAG, "Display mode activated")
                    _isDisplayMode.value = true
                    _currentLetter.value = letter
                    _currentColor.value = parseColor(colorHex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore parsing messaggio: ${e.message}", e)
        }
    }
    
   private fun handleReset(json: JSONObject) {
    _currentLetter.value = "?"
    _currentColor.value = Color.Gray
    _isDisplayMode.value = true
    
    // Vibrazione reset SOLO se abilitata nei settings
    try {
        val settings = if (json.has("settings")) {
            json.getJSONObject("settings").getJSONObject("watch")
        } else null
        
        val vibrationEnabled = settings?.optBoolean("vibrationEnabled", true) ?: true
        
        if (vibrationEnabled) {
            val duration = settings?.optInt("vibrationDuration", 700) ?: 700
            
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    duration.toLong(),
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
            Log.d(TAG, "📳 Reset vibration: ${duration}ms")
        } else {
            Log.d(TAG, "🔇 Reset vibration DISABLED by settings")
        }
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
                val patternList = mutableListOf<Long>()
                patternList.add(0) // Delay iniziale
                repeat(count) {
                    patternList.add(duration.toLong())
                    if (it < count - 1) {
                        patternList.add(pause.toLong())
                    }
                }
                patternList.toLongArray()
            }
            
            vibrator?.vibrate(
                VibrationEffect.createWaveform(timings, -1)
            )
            
            Log.d(TAG, "📳 Vibrazione $pattern: $letter ($count volte, ${duration}ms)")
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
            Log.e(TAG, "❌ Errore parsing colore: $hex", e)
            Color.Gray
        }
    }
}

@Composable
fun WatchReceiverApp(
    letter: String,
    color: Color,
    isDisplayMode: Boolean,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    
    Scaffold(
        timeText = { }
    ) {
        if (showSettings) {
            // ═══ SCHERMATA SETTINGS ═══
            SettingsScreen(
                fontSize = fontSize,
                onFontSizeChange = onFontSizeChange,
                onClose = { showSettings = false }
            )
        } else {
            // ═══ SCHERMATA PRINCIPALE ═══
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Contenuto centrale
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDisplayMode) {
                        // Modalità display: solo lettera
                        Text(
                            text = letter,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Modalità vibrazione: solo icona GRIGIA
                        Text(
                            text = "〰",
                            fontSize = 60.sp,
                            color = Color(0xFF808080),  // Grigio
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // Icona ingranaggio (top-right corner)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Button(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0x80000000)  // Nero semi-trasparente
                        )
                    ) {
                        Text(
                            text = "⚙",
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),  // Grigio scuro
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Titolo
            Text(
                text = "Settings",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Label dimensione font
            Text(
                text = "Font Size",
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            // Controlli dimensione
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { 
                        if (fontSize > 40) onFontSizeChange(fontSize - 10)
                    },
                    modifier = Modifier.size(45.dp),
                    enabled = fontSize > 40
                ) {
                    Text("-", fontSize = 24.sp)
                }
                
                Text(
                    text = "$fontSize",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Button(
                    onClick = { 
                        if (fontSize < 120) onFontSizeChange(fontSize + 10)
                    },
                    modifier = Modifier.size(45.dp),
                    enabled = fontSize < 120
                ) {
                    Text("+", fontSize = 24.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Pulsante chiudi
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(40.dp)
            ) {
                Text("Close", fontSize = 14.sp)
            }
        }
    }
}
