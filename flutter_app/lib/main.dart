import 'dart:convert';
import 'package:flutter/material.dart';
import 'models/settings_model.dart';
import 'settings_page.dart';
import 'services/wifi_communication_service.dart';
import 'services/watch_wifi_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Segna Controller',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const ControllerPage(),
    );
  }
}

class ControllerPage extends StatefulWidget {
  const ControllerPage({super.key});

  @override
  State<ControllerPage> createState() => _ControllerPageState();
}

class _ControllerPageState extends State<ControllerPage> {
  final WiFiCommunicationService _wifiService = WiFiCommunicationService();
  final WatchWiFiService _watchWifiService = WatchWiFiService();
  
  final TextEditingController _ipController = TextEditingController(text: '192.168.0.125');
  final TextEditingController _watchIpController = TextEditingController(text: '192.168.0.124');

  SettingsModel? settings;

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
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    settings = await SettingsModel.load();
    setState(() {});
  }

  Future<void> _openSettings() async {
    if (settings == null) return;
    final result = await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => SettingsPage(settings: settings!)),
    );
    if (result == true) {
      await _loadSettings();
    }
  }

  Future<void> _connectToESP32() async {
    final ip = _ipController.text.trim();
    if (ip.isEmpty) {
      _showError('⚠️ Inserisci un indirizzo IP valido');
      return;
    }
    _showMessage('🔌 Connessione a ESP32...');
    final success = await _wifiService.connect(ip);
    setState(() {});
    if (success) {
      _showMessage('✅ Connesso a ESP32: $ip');
    } else {
      _showError('❌ Impossibile connettersi a ESP32');
    }
  }

  void _disconnectFromESP32() {
    _wifiService.disconnect();
    setState(() {});
    _showMessage('🔌 Disconnesso da ESP32');
  }

  Future<void> _connectToWatchWiFi() async {
    final ip = _watchIpController.text.trim();
    if (ip.isEmpty) {
      _showError('⚠️ Inserisci IP Watch valido');
      return;
    }
    _showMessage('🔌 Connessione a Watch WiFi...');
    final success = await _watchWifiService.connect(ip);
    setState(() {});
    if (success) {
      _showMessage('✅ Connesso a Watch WiFi: $ip');
    } else {
      _showError('❌ Impossibile connettersi a Watch WiFi');
    }
  }

  void _disconnectFromWatchWiFi() {
    _watchWifiService.disconnect();
    setState(() {});
    _showMessage('🔌 Disconnesso da Watch WiFi');
  }

  Future<void> _sendCommand(String letter) async {
    if (settings == null) {
      _showError('⚠️ Impostazioni non caricate');
      return;
    }

    final data = letterData[letter]!;
    bool esp32Success = false;
    bool watchSuccess = false;

    // Send to ESP32 via WiFi
    if (_wifiService.isConnected) {
      esp32Success = await _wifiService.sendLetter(
        letter, 
        data['colorHex'],
        data['colorName'],
        settings!.toJson()
      );
      if (!esp32Success) {
        _showError('❌ Errore invio a ESP32');
      }
    }

    // Send to Watch via WiFi (no Bluetooth fallback)
    if (_watchWifiService.isConnected) {
      watchSuccess = await _watchWifiService.sendLetter(
        letter,
        data['colorHex'],
        data['colorName'],
        settings!.toJson()
      );
      if (!watchSuccess) {
        _showError('❌ Errore invio a Watch WiFi');
      }
    }

    // Show result
    if (esp32Success && watchSuccess) {
      _showMessage('✅ Comando inviato: $letter (ESP32 + Watch)');
    } else if (esp32Success) {
      _showMessage('✅ Comando inviato: $letter (solo ESP32)');
    } else if (watchSuccess) {
      _showMessage('✅ Comando inviato: $letter (solo Watch)');
    } else {
      _showError('❌ Nessun dispositivo connesso');
    }
  }

  Future<void> _sendReset() async {
    if (settings == null) return;

    // Send reset to ESP32
    if (_wifiService.isConnected) {
      await _wifiService.sendReset(settings!.toResetJson());
    }

    // Send reset to Watch via WiFi (no Bluetooth fallback)
    if (_watchWifiService.isConnected) {
      await _watchWifiService.sendReset(settings!.toResetJson());
    }

    _showMessage('🔄 Reset inviato');
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), duration: const Duration(seconds: 2)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('Segna Controller'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: _openSettings,
          ),
        ],
      ),
      body: Column(
        children: [
          const SizedBox(height: 16),
          // Two-column layout for ESP32 and Watch connections
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // ESP32 Connection Card
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: _wifiService.isConnected ? Colors.green : Colors.grey.shade300,
                        width: 2,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(
                              Icons.router,
                              color: _wifiService.isConnected ? Colors.green : Colors.grey,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Text(
                              '🔌 ESP32',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: _wifiService.isConnected ? Colors.green : Colors.grey,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        TextField(
                          controller: _ipController,
                          decoration: const InputDecoration(
                            hintText: '192.168.0.125',
                            isDense: true,
                            contentPadding: EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                            border: OutlineInputBorder(),
                          ),
                          style: const TextStyle(fontSize: 12),
                          keyboardType: const TextInputType.numberWithOptions(decimal: false),
                        ),
                        const SizedBox(height: 8),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _wifiService.isConnected ? _disconnectFromESP32 : _connectToESP32,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: _wifiService.isConnected ? Colors.red : Colors.green,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(vertical: 8),
                            ),
                            child: Text(
                              _wifiService.isConnected ? 'Disconnetti' : 'Connetti',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                // Watch Connection Card
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: _watchWifiService.isConnected ? Colors.blue : Colors.grey.shade300,
                        width: 2,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(
                              Icons.watch,
                              color: _watchWifiService.isConnected ? Colors.blue : Colors.grey,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Text(
                              '⌚ Watch WiFi',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: _watchWifiService.isConnected ? Colors.blue : Colors.grey,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        TextField(
                          controller: _watchIpController,
                          decoration: const InputDecoration(
                            hintText: '192.168.0.124',
                            isDense: true,
                            contentPadding: EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                            border: OutlineInputBorder(),
                          ),
                          style: const TextStyle(fontSize: 12),
                          keyboardType: const TextInputType.numberWithOptions(decimal: false),
                        ),
                        const SizedBox(height: 8),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _watchWifiService.isConnected ? _disconnectFromWatchWiFi : _connectToWatchWiFi,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: _watchWifiService.isConnected ? Colors.red : Colors.blue,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(vertical: 8),
                            ),
                            child: Text(
                              _watchWifiService.isConnected ? 'Disconnetti' : 'Connetti',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          // Command buttons section (A-E)
          Expanded(
            child: Center(
              child: SingleChildScrollView(
                child: Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 12,
                  runSpacing: 12,
                  children: letterData.entries.map((entry) {
                    return SizedBox(
                      width: 80,
                      height: 80,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: entry.value['color'] as Color,
                          foregroundColor: entry.key == 'A' || entry.key == 'B' ? Colors.black : Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          padding: EdgeInsets.zero,
                        ),
                        onPressed: () => _sendCommand(entry.key),
                        child: Text(
                          entry.key,
                          style: const TextStyle(fontSize: 36, fontWeight: FontWeight.bold),
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),
          ),
          // Reset button at bottom right
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Align(
              alignment: Alignment.bottomRight,
              child: SizedBox(
                width: 70,
                height: 70,
                child: FloatingActionButton(
                  backgroundColor: Colors.black,
                  foregroundColor: Colors.white,
                  onPressed: _sendReset,
                  child: const Text(
                    'Reset',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _wifiService.disconnect();
    _watchWifiService.disconnect();
    _ipController.dispose();
    _watchIpController.dispose();
    super.dispose();
  }
}
