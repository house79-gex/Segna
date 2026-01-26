package com.example.segnareceiver

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class AndroidServer(
    port: Int,
    private val onCommandReceived: (String) -> Unit
) : NanoHTTPD(port) {
    
    companion object {
        private const val TAG = "AndroidServer"
    }
    
    private val serverStartTime = System.currentTimeMillis()
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "📡 Richiesta: $method $uri")
        
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
                    """{"status":"ok","version":"1.0","uptime":$uptime,"device":"android"}"""
                )
            }
            
            method == Method.POST && uri == "/command" -> {
                try {
                    val bodyMap = mutableMapOf<String, String>()
                    session.parseBody(bodyMap)
                    val body = bodyMap["postData"] ?: ""
                    
                    Log.d(TAG, "📥 Comando: $body")
                    
                    // Verifica JSON valido
                    JSONObject(body)
                    
                    // Callback
                    onCommandReceived(body)
                    
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        """{"status":"success"}"""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Errore parsing: ${e.message}")
                    newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        """{"status":"error","message":"${e.message}"}"""
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
        
        // CORS headers
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        
        return response
    }
}
