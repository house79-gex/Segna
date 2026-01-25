package com.example.watchreceiver

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * WatchServer - HTTP Server per ricevere comandi dallo Smartphone
 * 
 * Porta: 5000
 * Endpoints:
 * - GET /status -> {"status":"ok","version":"1.0","uptime":12345}
 * - POST /command -> Riceve comandi JSON
 */
class WatchServer(
    private val onCommandReceived: (String) -> Unit
) : NanoHTTPD(5000) {
    
    companion object {
        private const val TAG = "WatchServer"
    }
    
    private val serverStartTime = System.currentTimeMillis()
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "📡 Richiesta ricevuta: $method $uri")
        
        // CORS headers
        val response: Response = when {
            method == Method.OPTIONS -> {
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            }
            
            method == Method.GET && uri == "/status" -> {
                val uptime = System.currentTimeMillis() - serverStartTime
                Log.d(TAG, "✅ Status check - Uptime: ${uptime}ms")
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"status":"ok","version":"1.0","uptime":$uptime}"""
                )
            }
            
            method == Method.POST && uri == "/command" -> {
                try {
                    // Leggi body della richiesta
                    val bodyMap = mutableMapOf<String, String>()
                    session.parseBody(bodyMap)
                    val body = bodyMap["postData"] ?: ""
                    
                    Log.d(TAG, "📥 Comando ricevuto: $body")
                    
                    // Verifica che sia JSON valido
                    JSONObject(body)
                    
                    // Invia al callback
                    onCommandReceived(body)
                    
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        """{"status": "success"}"""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Errore parsing comando: ${e.message}")
                    newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        """{"status": "error", "message": "${e.message}"}"""
                    )
                }
            }
            
            else -> {
                Log.w(TAG, "⚠️ Endpoint non trovato: $method $uri")
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not Found"
                )
            }
        }
        
        // Aggiungi CORS headers
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        
        return response
    }
}
