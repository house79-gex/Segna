package com.example.watchreceiver

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * WiFiReceiver - Gestisce la comunicazione WiFi con Smartphone
 * 
 * Sostituisce MessageClient di Wear OS Data Layer.
 * Effettua polling HTTP su Smartphone per ricevere aggiornamenti.
 */
class WiFiReceiver(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit
) {
    private var pollingJob: Job? = null
    private var esp32Ip: String? = null
    private var lastTimestamp: Long = 0
    
    // Polling configuration
    private val pollingIntervalMs = 500L // Poll every 500ms for ~2s max latency
    private val errorRetryDelayMs = 2000L // Wait longer on network errors
    
    companion object {
        private const val TAG = "WiFiReceiver"
    }
    
    /**
     * Connetti a Smartphone e inizia il polling
     * @param ipAddress Indirizzo IP dello Smartphone (es: "192.168.0.100")
     */
    fun connect(ipAddress: String) {
        esp32Ip = ipAddress
        startPolling()
        Log.d(TAG, "✅ Connesso a Smartphone: $ipAddress")
    }
    
    /**
     * Disconnetti da Smartphone e ferma il polling
     */
    fun disconnect() {
        stopPolling()
        esp32Ip = null
        Log.d(TAG, "🔌 Disconnesso da Smartphone")
    }
    
    /**
     * Avvia il polling HTTP periodico
     */
    private fun startPolling() {
        stopPolling()
        
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "🔄 Polling avviato")
            
            while (isActive) {
                try {
                    fetchUpdate()
                    delay(pollingIntervalMs)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Errore polling: ${e.message}")
                    delay(errorRetryDelayMs) // Use configurable error delay
                }
            }
            
            Log.d(TAG, "⏸ Polling terminato")
        }
    }
    
    /**
     * Ferma il polling HTTP
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    /**
     * Effettua una richiesta HTTP GET a Smartphone per ottenere lo stato corrente
     */
    private suspend fun fetchUpdate() {
        val ip = esp32Ip ?: return
        
        val url = URL("http://$ip:8080/receive")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                
                // Log solo per debug (commentare in produzione per ridurre log)
                // Log.d(TAG, "📡 Ricevuto: $response")
                
                val jsonObject = JSONObject(response)
                
                // Verifica se è un nuovo messaggio confrontando timestamp
                val timestamp = jsonObject.optLong("timestamp", 0)
                if (timestamp > lastTimestamp) {
                    lastTimestamp = timestamp
                    
                    Log.d(TAG, "✨ Nuovo messaggio ricevuto da smartphone (timestamp: $timestamp)")
                    
                    // Invia messaggio al main thread
                    withContext(Dispatchers.Main) {
                        onMessageReceived(response)
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Smartphone risponde con status code: $responseCode")
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "⏱ Timeout connessione Smartphone")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "❌ Smartphone non raggiungibile: $ip")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "❌ Impossibile connettersi a Smartphone: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore fetch: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Verifica se il receiver è connesso e sta effettuando polling
     */
    fun isConnected(): Boolean {
        return esp32Ip != null && pollingJob?.isActive == true
    }
    
    /**
     * Ottieni l'indirizzo IP corrente
     */
    fun getCurrentIp(): String? {
        return esp32Ip
    }
}
