import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'models/settings_model.dart';
import 'settings_page.dart';

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

  // Conferme ricezione dai dispositivi
  String? espLastConfirmedColor;
  String? watchLastConfirmedColor;

  // Impostazioni
  late SettingsModel settings;

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
    _loadSettings();
  }

  Future<void> _requestPermissions() async {
    await Permission.bluetoothScan.request();
    await Permission.bluetoothConnect.request();
    await Permission.location.request();
  }

  Future<void> _loadSettings() async {
    settings = await SettingsModel.load();
    setState(() {});
  }

  Future<void> _openSettings() async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SettingsPage(settings: settings),
      ),
    );
    if (result == true) {
      await _loadSettings();
    }
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
            _connectToESP32();
          }
          if (watchDevice == null &&
              (result.device.platformName.contains('Galaxy Watch') ||
                  result.device.platformName.contains('Watch'))) {
            watchDevice = result.device;
            _connectToWatch();
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
              // Abilita notifiche per ricevere conferme
              if (characteristic.properties.notify) {
                await characteristic.setNotifyValue(true);
                characteristic.value.listen((value) {
                  _handleDeviceNotification('esp32', value);
                });
              }
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
              // Abilita notifiche per ricevere conferme
              if (characteristic.properties.notify) {
                await characteristic.setNotifyValue(true);
                characteristic.value.listen((value) {
                  _handleDeviceNotification('watch', value);
                });
              }
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

  void _handleDeviceNotification(String deviceType, List<int> value) {
    try {
      final jsonString = String.fromCharCodes(value);
      final data = jsonDecode(jsonString);
      if (data['status'] == 'received') {
        setState(() {
          if (deviceType == 'esp32') {
            espLastConfirmedColor = data['color'];
          } else if (deviceType == 'watch') {
            watchLastConfirmedColor = data['color'];
          }
        });
      }
    } catch (e) {
      // Ignora errori di parsing
    }
  }

  Future<void> _sendCommand(String letter) async {
    final data = letterData[letter]!;
    final payload = jsonEncode({
      'letter': letter,
      'color': data['colorHex'],
      'colorName': data['colorName'],
      'settings': settings.toJson(),
    });

    await _sendToDevice(payload);

    // Reset conferme precedenti quando si invia un nuovo comando
    setState(() {
      espLastConfirmedColor = null;
      watchLastConfirmedColor = null;
    });
  }

  Future<void> _sendReset() async {
    final payload = jsonEncode({
      'command': 'RESET',
      'settings': settings.toResetJson(),
    });
    await _sendToDevice(payload);

    // Reset conferme
    setState(() {
      espLastConfirmedColor = null;
      watchLastConfirmedColor = null;
    });
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
        actions: [
          // Pulsante Settings
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: _openSettings,
          ),
          const SizedBox(width: 8),
          // Indicatore ESP32 con bordo colorato
          Container(
            decoration: BoxDecoration(
              border: Border.all(
                color: espLastConfirmedColor != null
                    ? _getColorFromHex(espLastConfirmedColor!)
                    : Colors.transparent,
                width: 3,
              ),
              borderRadius: BorderRadius.circular(8),
            ),
            padding: const EdgeInsets.all(4),
            child: Row(
              children: [
                Icon(
                  Icons.router,
                  color: espConnected ? Colors.green : Colors.grey,
                ),
                const SizedBox(width: 4),
                Text(
                  'ESP32',
                  style: TextStyle(
                    color: espConnected ? Colors.green : Colors.grey,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          // Indicatore Watch con bordo colorato
          Container(
            decoration: BoxDecoration(
              border: Border.all(
                color: watchLastConfirmedColor != null
                    ? _getColorFromHex(watchLastConfirmedColor!)
                    : Colors.transparent,
                width: 3,
              ),
              borderRadius: BorderRadius.circular(8),
            ),
            padding: const EdgeInsets.all(4),
            child: Row(
              children: [
                Icon(
                  Icons.watch,
                  color: watchConnected ? Colors.green : Colors.grey,
                ),
                const SizedBox(width: 4),
                Text(
                  'Watch',
                  style: TextStyle(
                    color: watchConnected ? Colors.green : Colors.grey,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          // Pulsante Scansiona
          IconButton(
            icon: Icon(isScanning
                ? Icons.hourglass_empty
                : Icons.bluetooth_searching),
            onPressed: isScanning ? null : _scanForDevices,
            tooltip: isScanning ? 'Scansione...' : 'Scansiona',
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: Stack(
        children: [
          // Pulsanti lettere in colonna centrale
          Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(vertical: 16.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: letterData.entries.map((entry) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8.0),
                    child: SizedBox(
                      width: 200,
                      height: 80,
                      child: ElevatedButton(
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
                          '${entry.key}\n${entry.value['colorName']}',
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
          // Pulsante Reset piccolo in basso a destra
          Positioned(
            bottom: 16,
            right: 16,
            child: SizedBox(
              width: 80,
              height: 80,
              child: FloatingActionButton(
                backgroundColor: Colors.black,
                foregroundColor: Colors.white,
                onPressed: _sendReset,
                child: const Text(
                  'RESET',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Color _getColorFromHex(String hexColor) {
    hexColor = hexColor.replaceAll('#', '');
    if (hexColor.length == 6) {
      hexColor = 'FF$hexColor';
    }
    return Color(int.parse(hexColor, radix: 16));
  }

  @override
  void dispose() {
    espDevice?.disconnect();
    watchDevice?.disconnect();
    super.dispose();
  }
}
