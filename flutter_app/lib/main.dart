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
  static const platform = MethodChannel('com.example.segna/wear');
  
  final WiFiCommunicationService _wifiService = WiFiCommunicationService();
  final WatchWiFiService _watchWifiService = WatchWiFiService();
  
  final TextEditingController _ipController = TextEditingController(text: '192.168.0.125');
  final TextEditingController _watchIpController = TextEditingController(text: '192.168.0.124');

  bool watchConnected = false;
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
    _connectToWatchBluetooth();
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

  Future<void> _connectToWatchBluetooth() async {
    try {
      final result = await platform.invokeMethod('connectToWatch');
      setState(() {
        watchConnected = result == true;
      });
      if (watchConnected) {
        _showMessage('Watch connesso via Bluetooth Wear OS');
      }
    } catch (e) {
      print('Error connecting to watch Bluetooth: $e');
      setState(() {
        watchConnected = false;
      });
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

