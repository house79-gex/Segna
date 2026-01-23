package com.example.watchreceiver

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class MainActivity : ComponentActivity() {
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var vibrator: Vibrator? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var notificationManager: NotificationManager? = null

    private val serviceUuid = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private val letterState = mutableStateOf("")
    private val colorState = mutableStateOf(android.graphics.Color.BLACK)
    private val isVibrationMode = mutableStateOf(false)
    
    // Settings
    private val displayModeState = mutableStateOf("BOTH")
    private val letterSizeState = mutableStateOf(120)
    private val colorSizeState = mutableStateOf("FULLSCREEN")
    
    // Permission launcher for Bluetooth permissions
    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // All permissions granted, setup BLE
            setupBLE()
        } else {
            // Permissions denied - app will not function properly
            android.util.Log.w("MainActivity", "Bluetooth permissions denied - BLE functionality will not work")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Load settings
        loadSettings()
        
        // Request Bluetooth permissions before setting up BLE
        requestBluetoothPermissionsIfNeeded()

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
    
    private fun requestBluetoothPermissionsIfNeeded() {
        // Android 12+ (API 31+) requires runtime permissions
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN
        )
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestBluetoothPermissions.launch(missingPermissions.toTypedArray())
        } else {
            // Permissions already granted
            setupBLE()
        }
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupBLE() {
        if (!hasBluetoothPermissions()) {
            return
        }
        
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        val gattServerCallback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (characteristic?.uuid == characteristicUuid && value != null) {
                    handleCommand(String(value))
                }

                if (responseNeeded && hasBluetoothPermissions()) {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                }
            }
        }

        bluetoothGattServer = bluetoothManager?.openGattServer(this, gattServerCallback)

        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        bluetoothGattServer?.addService(service)

        startAdvertising()
    }

    private fun startAdvertising() {
        if (!hasBluetoothPermissions()) {
            return
        }
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
            }
        }
        
        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun handleCommand(json: String) {
        try {
            val jsonObject = JSONObject(json)

            if (jsonObject.has("command") && jsonObject.getString("command") == "RESET") {
                // Gestione comando RESET
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

                letterState.value = ""
                colorState.value = android.graphics.Color.BLACK
                isVibrationMode.value = false

                // Invia conferma ricezione
                sendConfirmation("#000000")

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
                    // Modalità vibrazione: schermo nero + vibrazioni
                    isVibrationMode.value = true
                    colorState.value = android.graphics.Color.BLACK
                    letterState.value = ""

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
                    // Modalità display: lettera e colore
                    isVibrationMode.value = false
                    letterState.value = letter
                    colorState.value = color
                }

                // Invia conferma ricezione
                sendConfirmation(colorHex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendConfirmation(color: String) {
        if (!hasBluetoothPermissions()) {
            return
        }
        
        try {
            val confirmationJson = JSONObject()
            confirmationJson.put("status", "received")
            confirmationJson.put("device", "watch")
            confirmationJson.put("color", color)
            val confirmationBytes = confirmationJson.toString().toByteArray()

            // Invia notifica a tutti i dispositivi connessi
            bluetoothGattServer?.getService(serviceUuid)
                ?.getCharacteristic(characteristicUuid)?.let { characteristic ->
                    characteristic.value = confirmationBytes
                    bluetoothGattServer?.notifyCharacteristicChanged(
                        null,
                        characteristic,
                        false
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (hasBluetoothPermissions()) {
            bluetoothGattServer?.close()
            advertiseCallback?.let { callback ->
                bluetoothLeAdvertiser?.stopAdvertising(callback)
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
