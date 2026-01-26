package com.example.segnareceiver

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import org.json.JSONObject

class VibrationHandler(context: Context) {
    
    companion object {
        private const val TAG = "VibrationHandler"
    }
    
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    fun handleCommand(message: String) {
        try {
            val json = JSONObject(message)
            
            // RESET command
            if (json.has("command") && json.getString("command") == "RESET") {
                handleReset(json)
                return
            }
            
            // Lettera command
            if (json.has("letter")) {
                val letter = json.getString("letter")
                val settings = if (json.has("settings")) {
                    json.getJSONObject("settings").getJSONObject("watch")
                } else null
                
                val vibrationMode = settings?.optBoolean("vibrationMode", false) ?: false
                
                if (vibrationMode) {
                    vibrate(letter, settings)
                } else {
                    Log.d(TAG, "📺 Display mode - no vibration")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore: ${e.message}", e)
        }
    }
    
    private fun handleReset(json: JSONObject) {
        try {
            val settings = if (json.has("settings")) {
                json.getJSONObject("settings").getJSONObject("watch")
            } else null
            
            val vibrationEnabled = settings?.optBoolean("vibrationEnabled", true) ?: true
            
            if (vibrationEnabled) {
                val duration = settings?.optInt("vibrationDuration", 700) ?: 700
                
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        duration.toLong(),
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
                Log.d(TAG, "📳 Reset vibration: ${duration}ms")
            } else {
                Log.d(TAG, "🔇 Reset vibration DISABLED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore reset: ${e.message}")
        }
    }
    
    private fun vibrate(letter: String, settings: JSONObject?) {
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
                patternList.add(0)
                repeat(count) {
                    patternList.add(duration.toLong())
                    if (it < count - 1) {
                        patternList.add(pause.toLong())
                    }
                }
                patternList.toLongArray()
            }
            
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings, -1)
            )
            
            Log.d(TAG, "📳 Vibrazione $pattern: $letter ($count volte)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore vibrazione: ${e.message}")
        }
    }
}
