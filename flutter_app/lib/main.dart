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
      resizeToAvoidBottomInset: true,  // ← Permetti resize quando tastiera aperta
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
      body: SingleChildScrollView(  // ← Rendi scrollabile
        child: Column(
          children: [
            const SizedBox(height: 16),
            
            // ═══ SEZIONE CONNESSIONI (2 colonne) ═══
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // ─── Colonna ESP32 ───
                  Expanded(
                    child: Container(
                      decoration: BoxDecoration(
                        border: Border.all(
                          color: _wifiService.isConnected ? Colors.green : Colors.grey,
                          width: 3,
                        ),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: const [
                              Icon(Icons.router, size: 24),
                              SizedBox(width: 8),
                              Text('ESP32', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                            ],
                          ),
                          const SizedBox(height: 12),
                          TextField(
                            controller: _ipController,
                            decoration: const InputDecoration(
                              labelText: 'IP',
                              hintText: '192.168.0.125',
                              border: OutlineInputBorder(),
                              contentPadding: EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                            ),
                            keyboardType: TextInputType.numberWithOptions(decimal: true),
                            style: const TextStyle(fontSize: 14),
                          ),
                          const SizedBox(height: 8),
                          ElevatedButton(
                            onPressed: _wifiService.isConnected ? _disconnectFromESP32 : _connectToESP32,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: _wifiService.isConnected ? Colors.red : Colors.green,
                              foregroundColor: Colors.white,
                              minimumSize: const Size(double.infinity, 40),
                            ),
                            child: Text(_wifiService.isConnected ? 'Disconnetti' : 'Connetti'),
                          ),
                        ],
                      ),
                    ),
                  ),
                  
                  const SizedBox(width: 12),
                  
                  // ─── Colonna Watch ───
                  Expanded(
                    child: Container(
                      decoration: BoxDecoration(
                        border: Border.all(
                          color: _watchWifiService.isConnected ? Colors.green : Colors.grey,
                          width: 3,
                        ),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: const [
                              Icon(Icons.watch, size: 24),
                              SizedBox(width: 8),
                              Text('Watch', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                            ],
                          ),
                          const SizedBox(height: 12),
                          TextField(
                            controller: _watchIpController,
                            decoration: const InputDecoration(
                              labelText: 'IP',
                              hintText: '192.168.0.124',
                              border: OutlineInputBorder(),
                              contentPadding: EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                            ),
                            keyboardType: TextInputType.numberWithOptions(decimal: true),
                            style: const TextStyle(fontSize: 14),
                          ),
                          const SizedBox(height: 8),
                          ElevatedButton(
                            onPressed: _watchWifiService.isConnected ? _disconnectFromWatchWiFi : _connectToWatchWiFi,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: _watchWifiService.isConnected ? Colors.red : Colors.blue,
                              foregroundColor: Colors.white,
                              minimumSize: const Size(double.infinity, 40),
                            ),
                            child: Text(_watchWifiService.isConnected ? 'Disconnetti' : 'Connetti'),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            
            const SizedBox(height: 16),
            
            // ═══ PULSANTE RESET SOPRA ═══
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: _sendReset,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.black,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  child: const Text('RESET', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                ),
              ),
            ),
            
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 8),
            
            // ═══ PULSANTI LETTERE (COLONNA SINGOLA) ═══
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              child: Column(
                children: letterData.entries.map((entry) {
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12.0),
                    child: SizedBox(
                      width: double.infinity,  // Larghezza massima
                      height: 70,              // Altezza consistente
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: entry.value['color'] as Color,
                          foregroundColor: entry.key == 'A' || entry.key == 'B' ? Colors.black : Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          elevation: 4,
                        ),
                        onPressed: () => _sendCommand(entry.key),
                        child: Text(
                          entry.key,
                          style: const TextStyle(fontSize: 36, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
            
            const SizedBox(height: 20),
          ],
        ),
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
