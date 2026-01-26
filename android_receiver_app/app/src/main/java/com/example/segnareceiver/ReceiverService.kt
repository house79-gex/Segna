package com.example.segnareceiver

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class ReceiverService : Service() {
    
    companion object {
        private const val TAG = "ReceiverService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "segna_receiver_channel"
    }
    
    private var androidServer: AndroidServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrationHandler: VibrationHandler? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service creato")
        
        createNotificationChannel()
        
        // WakeLock PARTIAL (CPU attiva, schermo può dormire)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SegnaReceiver::ServerWakeLock"
        )
        wakeLock?.acquire()
        Log.d(TAG, "🔓 WakeLock PARTIAL acquisito")
        
        // Inizializza vibration handler
        vibrationHandler = VibrationHandler(this)
        
        // Avvia server HTTP
        startHttpServer()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "✅ Service in foreground")
        return START_STICKY  // Riavvia automaticamente se killato
    }
    
    private fun startHttpServer() {
        val port = 5001  // Porta diversa dal watch
        
        androidServer = AndroidServer(port) { message ->
            handleMessage(message)
        }
        
        try {
            androidServer?.start()
            
            val ipAddress = getLocalIpAddress()
            Log.d(TAG, "✅ Server HTTP avviato")
            Log.d(TAG, "📡 IP: $ipAddress:$port")
            
            // Aggiorna notifica con IP
            updateNotificationWithIp(ipAddress, port)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore avvio server: ${e.message}", e)
        }
    }
    
    private fun handleMessage(message: String) {
        Log.d(TAG, "📥 Messaggio ricevuto: $message")
        vibrationHandler?.handleCommand(message)
    }
    
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore rilevamento IP: ${e.message}")
        }
        return "unknown"
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Segna Receiver Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene il server HTTP attivo"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Segna Receiver")
            .setContentText("Server in attesa...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotificationWithIp(ip: String, port: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Segna Receiver Attivo")
            .setContentText("Server: $ip:$port")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        androidServer?.stop()
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        
        Log.d(TAG, "🛑 Service fermato")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
