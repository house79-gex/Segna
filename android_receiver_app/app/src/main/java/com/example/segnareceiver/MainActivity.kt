package com.example.segnareceiver

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private var serviceRunning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val statusText = findViewById<TextView>(R.id.statusText)
        val ipText = findViewById<TextView>(R.id.ipText)
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        
        // Rileva IP
        val ip = getLocalIpAddress()
        ipText.text = "IP: $ip:5001"
        
        startButton.setOnClickListener {
            startService()
            statusText.text = "Server ATTIVO ✅"
            serviceRunning = true
        }
        
        stopButton.setOnClickListener {
            stopService()
            statusText.text = "Server FERMO ⏸️"
            serviceRunning = false
        }
    }
    
    private fun startService() {
        val intent = Intent(this, ReceiverService::class.java)
        startForegroundService(intent)
    }
    
    private fun stopService() {
        val intent = Intent(this, ReceiverService::class.java)
        stopService(intent)
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
            return "unknown"
        }
        return "unknown"
    }
}
