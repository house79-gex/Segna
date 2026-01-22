import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Segna BLE Controller',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const BLEControllerPage(),
    );
  }
}

class BLEControllerPage extends StatefulWidget {
  const BLEControllerPage({super.key});

  @override
  State<BLEControllerPage> createState() => _BLEControllerPageState();
}

class _BLEControllerPageState extends State<BLEControllerPage> {
  BluetoothDevice? espDevice;
  BluetoothDevice? watchDevice;
  BluetoothCharacteristic? espCharacteristic;
  BluetoothCharacteristic? watchCharacteristic;

  bool isScanning = false;
  bool espConnected = false;
  bool watchConnected = false;

  final String espServiceUuid = '4fafc201-1fb5-459e-8fcc-c5c9c331914b';
  final String espCharacteristicUuid = 'beb5483e-36e1-4688-b7f5-ea07361b26a8';

  final Map<String, Map<String, dynamic>> letterData = {
    'A': {'color': Colors.white, 'colorHex': '#FFFFFF', 'colorName': 'WHITE'},
    'B': {'color': Colors.yellow, 'colorHex': '#FFFF00', 'colorName': 'YELLOW'},
    'C': {'color': Colors.green, 'colorHex': '#00FF00', 'colorName': 'GREEN'},
    'D': {'color': Colors.red, 'colorHex': '#FF0000', 'colorName': 'RED'},
    'E': {'color': Colors.blue, 'colorHex': '#0000FF', 'colorName': 'BLUE'},
  };

  @override
  void initState() {
    super.initState();
    _requestPermissions();
  }

  Future<void> _requestPermissions() async {
    await Permission.bluetoothScan.request();
    await Permission.bluetoothConnect.request();
    await Permission.location.request();
  }

  Future<void> _scanForDevices() async {
    setState(() {
      isScanning = true;
    });

    try {
      await FlutterBluePlus.startScan(timeout: const Duration(seconds: 4));

      FlutterBluePlus.scanResults.listen((results) {
        for (ScanResult result in results) {
          if (espDevice == null &&
              (result.device.platformName.contains('ESP32') ||
                  result.device.platformName.contains('Segna'))) {
            espDevice = result.device;
          }
          if (watchDevice == null &&
              (result.device.platformName.contains('Galaxy Watch') ||
                  result.device.platformName.contains('Watch'))) {
            watchDevice = result.device;
          }
        }
        setState(() {});
      });

      await Future.delayed(const Duration(seconds: 4));
      await FlutterBluePlus.stopScan();
    } catch (e) {
      _showError('Errore durante la scansione: $e');
    }

    setState(() {
      isScanning = false;
    });
  }

  Future<void> _connectToESP32() async {
    if (espDevice == null) return;

    try {
      await espDevice!.connect();
      List<BluetoothService> services = await espDevice!.discoverServices();

      for (var service in services) {
        if (service.uuid.toString().toLowerCase() ==
            espServiceUuid.toLowerCase()) {
          for (var characteristic in service.characteristics) {
            if (characteristic.uuid.toString().toLowerCase() ==
                espCharacteristicUuid.toLowerCase()) {
              espCharacteristic = characteristic;
            }
          }
        }
      }

      setState(() {
        espConnected = true;
      });
    } catch (e) {
      _showError('Errore connessione ESP32: $e');
    }
  }

  Future<void> _connectToWatch() async {
    if (watchDevice == null) return;

    try {
      await watchDevice!.connect();
      List<BluetoothService> services = await watchDevice!.discoverServices();

      for (var service in services) {
        if (service.uuid.toString().toLowerCase() ==
            espServiceUuid.toLowerCase()) {
          for (var characteristic in service.characteristics) {
            if (characteristic.uuid.toString().toLowerCase() ==
                espCharacteristicUuid.toLowerCase()) {
              watchCharacteristic = characteristic;
            }
          }
        }
      }

      setState(() {
        watchConnected = true;
      });
    } catch (e) {
      _showError('Errore connessione Watch: $e');
    }
  }

  Future<void> _sendCommand(String letter) async {
    final data = letterData[letter]!;
    final payload = jsonEncode({
      'letter': letter,
      'color': data['colorHex'],
      'colorName': data['colorName'],
    });

    await _sendToDevice(payload);
  }

  Future<void> _sendReset() async {
    final payload = jsonEncode({'command': 'RESET'});
    await _sendToDevice(payload);
  }

  Future<void> _sendToDevice(String payload) async {
    final bytes = utf8.encode(payload);

    if (espCharacteristic != null) {
      try {
        await espCharacteristic!.write(bytes);
      } catch (e) {
        _showError('Errore invio ESP32: $e');
      }
    }

    if (watchCharacteristic != null) {
      try {
        await watchCharacteristic!.write(bytes);
      } catch (e) {
        _showError('Errore invio Watch: $e');
      }
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('Segna BLE Controller'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Connection Status
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        Column(
                          children: [
                            Icon(
                              Icons.watch,
                              color: watchConnected ? Colors.green : Colors.grey,
                              size: 40,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              watchConnected ? 'Watch OK' : 'Watch',
                              style: TextStyle(
                                color: watchConnected ? Colors.green : Colors.grey,
                              ),
                            ),
                          ],
                        ),
                        Column(
                          children: [
                            Icon(
                              Icons.router,
                              color: espConnected ? Colors.green : Colors.grey,
                              size: 40,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              espConnected ? 'ESP32 OK' : 'ESP32',
                              style: TextStyle(
                                color: espConnected ? Colors.green : Colors.grey,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        ElevatedButton.icon(
                          onPressed: isScanning ? null : _scanForDevices,
                          icon: Icon(isScanning
                              ? Icons.hourglass_empty
                              : Icons.bluetooth_searching),
                          label: Text(isScanning ? 'Scansione...' : 'Scansiona'),
                        ),
                        ElevatedButton.icon(
                          onPressed: watchDevice == null ? null : _connectToWatch,
                          icon: const Icon(Icons.link),
                          label: const Text('Connetti Watch'),
                        ),
                        ElevatedButton.icon(
                          onPressed: espDevice == null ? null : _connectToESP32,
                          icon: const Icon(Icons.link),
                          label: const Text('Connetti ESP32'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 32),
            // Letter Buttons
            Expanded(
              child: GridView.count(
                crossAxisCount: 3,
                mainAxisSpacing: 16,
                crossAxisSpacing: 16,
                children: letterData.entries.map((entry) {
                  return ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: entry.value['color'] as Color,
                      foregroundColor: entry.key == 'A' ||
                              entry.key == 'B' ||
                              entry.key == 'C'
                          ? Colors.black
                          : Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    onPressed: () => _sendCommand(entry.key),
                    child: Text(
                      entry.key,
                      style: const TextStyle(
                        fontSize: 48,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 16),
            // Reset Button
            SizedBox(
              width: double.infinity,
              height: 60,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.black,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                onPressed: _sendReset,
                child: const Text(
                  'RESET',
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    espDevice?.disconnect();
    watchDevice?.disconnect();
    super.dispose();
  }
}
