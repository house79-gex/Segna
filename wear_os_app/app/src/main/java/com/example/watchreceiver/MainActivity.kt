package com.example.watchreceiver

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.*

class MainActivity : ComponentActivity() {
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private val serviceUuid = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val characteristicUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private val letterState = mutableStateOf("A")
    private val colorState = mutableStateOf(android.graphics.Color.BLACK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBLE()

        setContent {
            WatchDisplay(letterState.value, colorState.value)
        }
    }

    private fun setupBLE() {
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

                if (responseNeeded) {
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
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)
        bluetoothGattServer?.addService(service)

        startAdvertising()
    }

    private fun startAdvertising() {
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
                letterState.value = ""
                colorState.value = android.graphics.Color.BLACK
            } else if (jsonObject.has("letter")) {
                val letter = jsonObject.getString("letter")
                val colorHex = jsonObject.getString("color")
                val color = android.graphics.Color.parseColor(colorHex)

                letterState.value = letter
                colorState.value = color
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGattServer?.close()
        bluetoothLeAdvertiser?.stopAdvertising(null)
    }
}

@Composable
fun WatchDisplay(letter: String, androidColor: Int) {
    val composeColor = ComposeColor(
        red = android.graphics.Color.red(androidColor) / 255f,
        green = android.graphics.Color.green(androidColor) / 255f,
        blue = android.graphics.Color.blue(androidColor) / 255f,
        alpha = 1f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(composeColor),
        contentAlignment = Alignment.Center
    ) {
        if (letter.isNotEmpty()) {
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
