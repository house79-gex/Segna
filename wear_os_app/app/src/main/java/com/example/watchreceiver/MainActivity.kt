package com.example.watchreceiver

import android.os.Bundle
import android.os.PowerManager
import android.content.Context
import android.util.Log
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
import com.example.watchreceiver.ui.theme.WatchReceiverTheme
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private var watchServer: WatchServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Stato per UI
    private val _currentLetter = mutableStateOf("?")
    private val _currentColor = mutableStateOf(Color.Gray)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "🚀 Avvio applicazione Watch Receiver")
        
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
        
        // Acquisisci WakeLock per mantenere server attivo
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WatchReceiver::ServerWakeLock"
        )
        wakeLock?.acquire()
        Log.d(TAG, "🔒 WakeLock acquisito - server rimarrà attivo")
        
        setContent {
            WatchReceiverTheme {
                WatchReceiverScreen(
                    letter = _currentLetter.value,
                    color = _currentColor.value
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Ferma server
        watchServer?.stop()
        Log.d(TAG, "🛑 Server HTTP fermato")
        
        // Rilascia WakeLock
        wakeLock?.release()
        Log.d(TAG, "🔓 WakeLock rilasciato")
    }
    
    /**
     * Gestisce i messaggi ricevuti dal server HTTP
     */
    private fun handleReceivedMessage(message: String) {
        try {
            Log.d(TAG, "📥 Messaggio ricevuto: $message")
            
            val json = JSONObject(message)
            
            // Controlla se è un comando RESET
            if (json.has("command") && json.getString("command") == "RESET") {
                Log.d(TAG, "🔄 Comando RESET ricevuto")
                resetDisplay()
                return
            }
            
            // Altrimenti gestisci come lettera
            if (json.has("letter") && json.has("color")) {
                val letter = json.getString("letter")
                val colorHex = json.getString("color")
                
                Log.d(TAG, "🎨 Lettera ricevuta: $letter, colore: $colorHex")
                
                // Converti hex a Color
                val color = parseColor(colorHex)
                
                // Aggiorna UI
                _currentLetter.value = letter
                _currentColor.value = color
                
                Log.d(TAG, "✅ Display aggiornato: $letter con colore $colorHex")
            } else {
                Log.w(TAG, "⚠️ Formato messaggio non valido: mancano 'letter' o 'color'")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore parsing messaggio: ${e.message}", e)
        }
    }
    
    /**
     * Reset del display
     */
    private fun resetDisplay() {
        _currentLetter.value = "?"
        _currentColor.value = Color.Gray
        Log.d(TAG, "🔄 Display resettato")
    }
    
    /**
     * Converte stringa colore hex (#RRGGBB) in Color
     */
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

/**
 * UI Composable per Wear OS
 */
@Composable
fun WatchReceiverScreen(
    letter: String = "?",
    color: Color = Color.Gray
) {
    Scaffold(
        timeText = {
            TimeText()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}
