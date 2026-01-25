import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
  // Platform channel for Wear OS communication (kept for watch communication)
  static const platform = MethodChannel('com.example.segna/wear');
  
  // WiFi Communication Services
  final WiFiCommunicationService _wifiService = WiFiCommunicationService();
  final WatchWiFiService _watchWifiService = WatchWiFiService();
  final TextEditingController _ipController = TextEditingController(text: '192.168.0.125');
  final TextEditingController _watchIpController = TextEditingController(text: '192.168.0.124');

  bool watchConnected = false;

  // Impostazioni
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
    _connectToWatch();
  }

  Future<void> _loadSettings() async {
    settings = await SettingsModel.load();
    setState(() {});
  }

  Future<void> _openSettings() async {
    if (settings == null) return;

    final result = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SettingsPage(settings: settings!),
      ),
    );
    if (result == true) {
      await _loadSettings();
    }
  }

  // Connect to Wear OS watch via platform channel
  Future<void> _connectToWatch() async {
    try {
      final result = await platform.invokeMethod('connectToWatch');
      setState(() {
        watchConnected = result == true;
      });
      if (watchConnected) {
        _showMessage('Watch connesso via Wear OS');
      }
    } catch (e) {
      print('Error connecting to watch: $e');
      setState(() {
        watchConnected = false;
      });
    }
  }

  // Connetti a ESP32 via WiFi
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

  // Disconnetti da ESP32
  void _disconnectFromESP32() {
    _wifiService.disconnect();
    setState(() {});
    _showMessage('🔌 Disconnesso da ESP32');
  }

  // Connetti a Watch via WiFi
  Future<void> _connectToWatch() async {
    final ip = _watchIpController.text.trim();
    if (ip.isEmpty) {
      _showError('⚠️ Inserisci un indirizzo IP valido per il Watch');
      return;
    }

    _showMessage('🔌 Connessione a Watch...');
    
    final success = await _watchWifiService.connect(ip);
    setState(() {
      watchConnected = success;
    });
    
    if (success) {
      _showMessage('✅ Connesso a Watch: $ip');
    } else {
      _showError('❌ Impossibile connettersi a Watch');
    }
  }

  // Disconnetti da Watch
  void _disconnectFromWatch() {
    _watchWifiService.disconnect();
    setState(() {
      watchConnected = false;
    });
    _showMessage('🔌 Disconnesso da Watch');
  }

  Future<void> _sendCommand(String letter) async {
    if (settings == null) {
      _showError('⚠️ Impostazioni non caricate');
      return;
    }

    if (!_wifiService.isConnected) {
      _showError('⚠️ Non connesso a ESP32. Connetti prima di inviare comandi.');
      return;
    }

    final data = letterData[letter]!;
    
    // Invia a ESP32 via WiFi
    final success = await _wifiService.sendLetter(
      letter, 
      data['colorHex'],
      data['colorName'],
      settings!.toJson()
    );

    if (!success) {
      _showError('❌ Errore invio a ESP32');
      return;
    }

    // Invia a Watch via WiFi (con fallback Bluetooth)
    if (watchConnected && _watchWifiService.isConnected) {
      // Prova WiFi prima
      final watchSuccess = await _watchWifiService.sendCommand(
        letter,
        data['colorHex'],
        data['colorName'],
        settings!.toJson()
      );
      
      if (!watchSuccess) {
        // Fallback a Bluetooth (Wear OS Data Layer) se WiFi fallisce
        print('⚠️ WiFi Watch fallito, provo Bluetooth...');
        try {
          final payload = jsonEncode({
            'letter': letter,
            'color': data['colorHex'],
            'colorName': data['colorName'],
            'settings': settings!.toJson(),
          });
          await platform.invokeMethod('sendMessage', {'message': payload});
          print('✅ Inviato a Watch via Bluetooth');
        } catch (e) {
          _showError('⚠️ Errore invio Watch (WiFi + Bluetooth): $e');
        }
      }
    } else if (watchConnected) {
      // Solo Bluetooth se Watch non connesso via WiFi
      try {
        final payload = jsonEncode({
          'letter': letter,
          'color': data['colorHex'],
          'colorName': data['colorName'],
          'settings': settings!.toJson(),
        });
        await platform.invokeMethod('sendMessage', {'message': payload});
      } catch (e) {
        _showError('⚠️ Errore invio Watch: $e');
      }
    }

    _showMessage('✅ Comando inviato: $letter');
  }

  Future<void> _sendReset() async {
    if (settings == null) return;

    // Invia a ESP32 via WiFi
    if (_wifiService.isConnected) {
      await _wifiService.sendReset(settings!.toResetJson());
    }

    // Invia a Watch via WiFi (con fallback Bluetooth)
    if (watchConnected && _watchWifiService.isConnected) {
      final watchSuccess = await _watchWifiService.sendReset(settings!.toResetJson());
      
      if (!watchSuccess) {
        // Fallback a Bluetooth
        try {
          final payload = jsonEncode({
            'command': 'RESET',
            'settings': settings!.toResetJson(),
          });
          await platform.invokeMethod('sendMessage', {'message': payload});
        } catch (e) {
          print('Errore invio Watch: $e');
        }
      }
    } else if (watchConnected) {
      // Solo Bluetooth se Watch non connesso via WiFi
      try {
        final payload = jsonEncode({
          'command': 'RESET',
          'settings': settings!.toResetJson(),
        });
        await platform.invokeMethod('sendMessage', {'message': payload});
      } catch (e) {
        print('Errore invio Watch: $e');
      }
    }

    _showMessage('🔄 Reset inviato');
  }

  Future<void> _closeWatchApp() async {
    try {
      await platform.invokeMethod('closeWatchApp');
      _showMessage('Comando chiusura inviato al watch');
    } catch (e) {
      _showError('Errore chiusura watch: $e');
    }
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
      body: Stack(
        children: [
          Column(
            children: [
              const SizedBox(height: 16),
              // Sezione connessione WiFi
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: Column(
                  children: [
                    // ESP32 Connection
                    TextField(
                      controller: _ipController,
                      decoration: const InputDecoration(
                        labelText: 'IP ESP32',
                        hintText: '192.168.0.125',
                        prefixIcon: Icon(Icons.router),
                        border: OutlineInputBorder(),
                      ),
                      keyboardType: const TextInputType.numberWithOptions(decimal: false),
                    ),
                    const SizedBox(height: 8),
                    ElevatedButton.icon(
                      onPressed: _wifiService.isConnected ? _disconnectFromESP32 : _connectToESP32,
                      icon: Icon(_wifiService.isConnected ? Icons.wifi_off : Icons.wifi),
                      label: Text(_wifiService.isConnected ? 'Disconnetti ESP32' : 'Connetti ESP32'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: _wifiService.isConnected ? Colors.red : Colors.green,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
                      ),
                    ),
                    const SizedBox(height: 16),
                    // Watch Connection
                    TextField(
                      controller: _watchIpController,
                      decoration: const InputDecoration(
                        labelText: 'IP Watch',
                        hintText: '192.168.0.124',
                        prefixIcon: Icon(Icons.watch),
                        border: OutlineInputBorder(),
                      ),
                      keyboardType: const TextInputType.numberWithOptions(decimal: false),
                    ),
                    const SizedBox(height: 8),
                    ElevatedButton.icon(
                      onPressed: watchConnected ? _disconnectFromWatch : _connectToWatch,
                      icon: Icon(watchConnected ? Icons.wifi_off : Icons.wifi),
                      label: Text(watchConnected ? 'Disconnetti Watch' : 'Connetti Watch'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: watchConnected ? Colors.red : Colors.blue,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              // Stato dispositivi
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: _wifiService.isConnected ? Colors.green : Colors.transparent,
                        width: 3,
                      ),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    padding: const EdgeInsets.all(8),
                    child: Row(
                      children: [
                        Icon(
                          Icons.router,
                          color: _wifiService.isConnected ? Colors.green : Colors.grey,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          'ESP32 WiFi',
                          style: TextStyle(
                            color: _wifiService.isConnected ? Colors.green : Colors.grey,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  Container(
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: watchConnected ? Colors.green : Colors.transparent,
                        width: 3,
                      ),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    padding: const EdgeInsets.all(8),
                    child: Row(
                      children: [
                        Icon(
                          Icons.watch,
                          color: watchConnected ? Colors.green : Colors.grey,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          'Watch WiFi',
                          style: TextStyle(
                            color: watchConnected ? Colors.green : Colors.grey,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              // Close Watch App button
              if (watchConnected)
                TextButton.icon(
                  icon: const Icon(Icons.close, size: 16),
                  label: const Text('Chiudi App Watch'),
                  onPressed: _closeWatchApp,
                  style: TextButton.styleFrom(
                    foregroundColor: Colors.red,
                  ),
                ),
              const SizedBox(height: 8),
              // Pulsanti lettere in colonna centrale
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.symmetric(vertical: 16.0),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: letterData.entries.map((entry) {
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 8.0),
                        child: SizedBox(
                          width: 200,
                          height: 60,
                          child: ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: entry.value['color'] as Color,
                              foregroundColor: entry.key == 'A' || entry.key == 'B'
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
                                fontSize: 32,
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
            ],
          ),
          // Pulsante Reset piccolo in basso a destra
          Positioned(
            bottom: 16,
            right: 16,
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
    _wifiService.disconnect();
    _watchWifiService.disconnect();
    _ipController.dispose();
    _watchIpController.dispose();
    super.dispose();
  }
}
