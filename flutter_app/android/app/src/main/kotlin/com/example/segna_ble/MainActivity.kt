package com.example.segna_ble

import android.os.Bundle
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.segna/wear"
    private val MESSAGE_PATH = "/segna_channel"
    private val TAG = "SegnaMainActivity"
    
    private lateinit var messageClient: MessageClient
    private var connectedNodes: List<Node> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Wear OS Message Client
        messageClient = Wearable.getMessageClient(this)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "connectToWatch" -> {
                    connectToWatch(result)
                }
                "sendMessage" -> {
                    val message = call.argument<String>("message")
                    if (message != null) {
                        sendMessageToWatch(message, result)
                    } else {
                        result.error("INVALID_ARGUMENT", "Message is null", null)
                    }
                }
                "closeWatchApp" -> {
                    closeWatchApp(result)
                }
                "isWatchConnected" -> {
                    checkWatchConnection(result)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun connectToWatch(result: MethodChannel.Result) {
        val nodeClient = Wearable.getNodeClient(this)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            connectedNodes = nodes
            if (nodes.isNotEmpty()) {
                Log.d(TAG, "Found ${nodes.size} connected watch(es)")
                result.success(true)
            } else {
                Log.w(TAG, "No connected watches found")
                result.success(false)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to get connected nodes", exception)
            result.error("CONNECTION_ERROR", exception.message, null)
        }
    }

    private fun sendMessageToWatch(message: String, result: MethodChannel.Result) {
        if (connectedNodes.isEmpty()) {
            // Try to refresh connected nodes
            val nodeClient = Wearable.getNodeClient(this)
            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                connectedNodes = nodes
                if (nodes.isNotEmpty()) {
                    sendToAllNodes(message, result)
                } else {
                    result.error("NO_WATCH", "No watch connected", null)
                }
            }.addOnFailureListener { exception ->
                result.error("SEND_ERROR", exception.message, null)
            }
        } else {
            sendToAllNodes(message, result)
        }
    }

    private fun sendToAllNodes(message: String, result: MethodChannel.Result) {
        val data = message.toByteArray(Charsets.UTF_8)
        var successCount = 0
        var failureCount = 0
        
        for (node in connectedNodes) {
            messageClient.sendMessage(node.id, MESSAGE_PATH, data)
                .addOnSuccessListener {
                    successCount++
                    Log.d(TAG, "Message sent successfully to ${node.displayName}")
                    if (successCount + failureCount == connectedNodes.size) {
                        if (successCount > 0) {
                            result.success(true)
                        } else {
                            result.error("SEND_ERROR", "Failed to send to any node", null)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    failureCount++
                    Log.e(TAG, "Failed to send message to ${node.displayName}", exception)
                    if (successCount + failureCount == connectedNodes.size) {
                        if (successCount > 0) {
                            result.success(true)
                        } else {
                            result.error("SEND_ERROR", exception.message, null)
                        }
                    }
                }
        }
    }

    private fun closeWatchApp(result: MethodChannel.Result) {
        val closeMessage = """{"closeApp": true}"""
        sendMessageToWatch(closeMessage, result)
    }

    private fun checkWatchConnection(result: MethodChannel.Result) {
        val nodeClient = Wearable.getNodeClient(this)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            connectedNodes = nodes
            result.success(nodes.isNotEmpty())
        }.addOnFailureListener { exception ->
            result.error("CHECK_ERROR", exception.message, null)
        }
    }
}
