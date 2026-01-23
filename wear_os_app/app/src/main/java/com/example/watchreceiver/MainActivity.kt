package com.example.watchreceiver

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
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

    private val serviceUuid = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private val letterState = mutableStateOf("")
    private val colorState = mutableStateOf(android.graphics.Color.BLACK)
    private val isVibrationMode = mutableStateOf(false)
    
    // Permission launcher for Bluetooth permissions
    private val requestBluetoothPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // All permissions granted, setup BLE
            setupBLE()
        } else {
            // Permissions denied - log or show message
            // For now, we'll just try to setup anyway (will fail gracefully)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Request Bluetooth permissions before setting up BLE
        requestBluetoothPermissionsIfNeeded()

        setContent {
            WatchDisplay(
                letter = letterState.value,
                androidColor = colorState.value,
                isVibrationMode = isVibrationMode.value
            )
        }
    }
    
    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        } else {
            // Android 11 and below - permissions handled by manifest
            setupBLE()
        }
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
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

        bluetoothLeAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
            }
        })
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
                    // Modalità display: lettera e colore a schermo intero
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
            bluetoothLeAdvertiser?.stopAdvertising(null)
        }
    }
}

@Composable
fun WatchDisplay(letter: String, androidColor: Int, isVibrationMode: Boolean) {
    val composeColor = if (isVibrationMode) {
        ComposeColor.Black
    } else {
        ComposeColor(
            red = android.graphics.Color.red(androidColor) / 255f,
            green = android.graphics.Color.green(androidColor) / 255f,
            blue = android.graphics.Color.blue(androidColor) / 255f,
            alpha = 1f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(composeColor),
        contentAlignment = Alignment.Center
    ) {
        if (!isVibrationMode && letter.isNotEmpty()) {
            Text(
                text = letter,
                fontSize = 120.sp,
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
    }
}
